# How Rerank Models Work

A **rerank model** (or "reranker") is a model that takes a list of candidate items — usually already retrieved by a fast, approximate search step — and re-orders them by how relevant they truly are to a query. Reranking is the second stage of the two-stage **retrieve-then-rerank** pattern that almost every modern search, recommendation, and RAG (retrieval-augmented generation) system uses.

---

## 1. Why reranking exists: the retrieve-then-rerank pattern

Searching over millions (or billions) of items with a highly accurate model is too slow to do at query time. So systems split the problem into two stages:

```
                    ┌─────────────────┐        ┌──────────────────┐
   Query  ───────▶  │  Stage 1:        │──────▶ │  Stage 2:         │──────▶ Final results
                    │  Retrieval       │  top   │  Reranking        │  reordered
                    │  (fast, cheap,   │  50-1000│ (slow, accurate,  │  top 10-20
                    │  approximate)    │ candidates│ precise)        │
                    └─────────────────┘        └──────────────────┘
```

### Stage 1 — Retrieval (recall-oriented)
- Goal: cheaply narrow millions of items down to a few hundred plausible candidates.
- Typical methods:
  - **Lexical search** (BM25, TF-IDF, Elasticsearch/OpenSearch) — matches keywords.
  - **Dense vector search / ANN** (bi-encoder embeddings + FAISS, HNSW, pgvector, Pinecone, etc.) — matches semantic meaning via nearest-neighbor lookup in vector space.
- Optimized for **speed** and **recall** ("don't miss the good stuff"), not precision. It's fine if the top-500 list contains junk, as long as the truly relevant items are somewhere in there.

### Stage 2 — Reranking (precision-oriented)
- Goal: take those ~50–1000 candidates and score them much more carefully, so the top 10–20 that get shown to the user (or fed to an LLM) are genuinely the best ones.
- Optimized for **precision/ranking quality**, not speed — it's only run on a small candidate set, so it can afford to be computationally expensive per item.

This split exists because of a fundamental trade-off: the most accurate relevance models (cross-encoders, see below) are too slow to run over an entire corpus, but too good not to use somewhere.

---

## 2. Bi-encoders vs. cross-encoders (the core architectural idea)

This is the single most important concept to understand rerankers.

### Bi-encoder (used in Stage 1 retrieval)

```
Query ──▶ [Encoder] ──▶ vector_q
                                    similarity(vector_q, vector_d) = cosine / dot product
Doc   ──▶ [Encoder] ──▶ vector_d
```

- The query and the document are each encoded **independently** into fixed-size vectors (embeddings), with the *same* encoder (hence "bi-" = two passes through one shared model, or two towers).
- Similarity is computed afterward with a cheap operation (cosine similarity, dot product).
- **Key advantage:** document embeddings can be pre-computed and indexed **once**, offline. At query time you only embed the query and do a fast vector lookup (ANN search). This is what makes it scale to billions of documents.
- **Key limitation:** because query and document never "see" each other during encoding, the model can't model fine-grained token-level interactions between them. It only compares two summary vectors. This caps how precise it can be.

### Cross-encoder (used in Stage 2 reranking — the classic reranker architecture)

```
[CLS] query [SEP] document [SEP] ──▶ [Transformer] ──▶ single relevance score
```

- The query and document are **concatenated into a single input sequence** and passed through the transformer **together**.
- Every token of the query can attend (via self-attention) to every token of the document, and vice versa, at every layer.
- This lets the model capture deep, fine-grained interactions — negations, exact phrase matches, contradictions, entity overlap, subtle semantic nuance — that a bi-encoder's two independent vectors simply cannot represent.
- **Result:** cross-encoders are consistently and significantly more accurate at judging relevance than bi-encoders.
- **Cost:** you cannot pre-compute anything. Every (query, document) pair requires a full forward pass through the transformer. This is why cross-encoders are only run on a small shortlist, never the whole corpus.

**This is the whole reason rerankers exist:** get bi-encoder speed for narrowing down millions of items, then spend cross-encoder accuracy on the last mile where it counts.

| | Bi-encoder | Cross-encoder (reranker) |
|---|---|---|
| Input | Query and doc encoded separately | Query + doc encoded jointly |
| Interaction | None until the final dot product | Full token-to-token attention |
| Precomputable | Yes — doc embeddings cached | No — every pair needs a fresh pass |
| Speed at scale | Fast (ANN over millions) | Slow (only feasible on small candidate sets) |
| Accuracy | Good | Best |
| Used for | Stage 1 retrieval | Stage 2 reranking |

---

## 3. What actually happens inside a cross-encoder reranker

1. **Input construction.** For each candidate document `d_i` returned by retrieval, build a text pair: `[CLS] query [SEP] d_i [SEP]` (this is standard BERT-style pair encoding; exact special tokens vary by model family).
2. **Joint encoding.** Feed this into a transformer (often a BERT/RoBERTa/DeBERTa-sized encoder, or increasingly a decoder-only LLM used as a scorer). Self-attention lets query tokens and document tokens interact directly.
3. **Score head.** Take the `[CLS]` token's final hidden state (or a pooled representation) and pass it through a small linear layer to produce a single scalar: the relevance score for that pair.
4. **Repeat per candidate.** This is done once per (query, candidate) pair — for N candidates, that's N forward passes (batched for efficiency, but still N× the cost of one bi-encoder query embedding).
5. **Sort.** Sort all candidates by score, descending. That's the reranked list.

```python
# Conceptual pseudocode for a cross-encoder reranker
scores = []
for doc in candidates:                # candidates = e.g. top-200 from vector search
    pair_input = tokenizer(query, doc, truncation=True, max_length=512)
    score = model(pair_input).logit   # single scalar relevance score
    scores.append((doc, score))

reranked = sorted(scores, key=lambda x: x[1], reverse=True)
top_k = reranked[:10]
```

Because this is O(N) transformer forward passes, reranking is typically only applied to the top 50–200 candidates from retrieval — not the full corpus.

---

## 4. How rerankers are trained

Rerankers are trained as a **supervised ranking/classification problem** on (query, document, relevance) triples.

### Training data
- Query, positive (relevant) document, and one or more negative (irrelevant or less relevant) documents.
- Real-world sources: search click logs, human relevance judgments (e.g. MS MARCO passage ranking dataset), LLM-generated relevance labels, or distillation from a larger teacher model.

### Loss functions
1. **Pointwise:** treat it as binary classification — "is this document relevant to this query?" (BCE loss on relevant=1 / irrelevant=0). Simple but doesn't directly optimize ranking order.
2. **Pairwise:** given `(query, doc_pos, doc_neg)`, train the model so `score(query, doc_pos) > score(query, doc_neg)` — e.g. margin ranking loss or the pairwise logistic loss used in **RankNet**.
3. **Listwise:** optimize over an entire ranked list at once using ranking metrics directly, e.g. **LambdaRank**/**LambdaMART**-style losses that approximate NDCG, or `ListNet`/`ListMLE`. This most directly matches how rerankers are evaluated in practice.

### Hard negative mining
The single biggest lever for reranker quality: don't just train against random negatives (too easy — the model learns nothing useful). Instead mine **hard negatives** — documents that a bi-encoder or BM25 scored highly but that are actually irrelevant. This forces the cross-encoder to learn the subtle distinctions that matter, which is exactly the job it needs to do (since its whole purpose is to fix retrieval's mistakes).

### Knowledge distillation
Many production rerankers are trained by distilling from an even larger/slower model (or an LLM given a relevance-judging prompt) — the big model labels a huge number of (query, doc) pairs with relevance scores, and the smaller reranker is trained to mimic those scores. This gets most of the big model's judgment quality at a fraction of the inference cost.

---

## 5. Evaluation metrics

Reranking quality is measured with **ranking metrics**, not simple accuracy, because *order* matters:

- **NDCG@k (Normalized Discounted Cumulative Gain):** rewards relevant items near the top of the list more than relevant items further down; the "gold standard" ranking metric.
- **MRR (Mean Reciprocal Rank):** average of `1 / rank_of_first_relevant_result` across queries — good when there's typically one right answer.
- **Recall@k / Precision@k:** how many of the truly relevant items appear in the top k.
- **MAP (Mean Average Precision):** averages precision at each rank where a relevant item appears.

A good reranker should measurably improve NDCG/MRR over the retrieval stage's raw ordering — that delta is literally the reranker's reason for existing.

---

## 6. Types of rerankers in practice

| Type | Description | Examples |
|---|---|---|
| **Cross-encoder transformers** | The classic architecture described above; BERT-sized models fine-tuned for pairwise relevance scoring. | `cross-encoder/ms-marco-MiniLM-L-6-v2`, `BAAI/bge-reranker-base/large`, `mixedbread-ai/mxbai-rerank`, Cohere Rerank |
| **LLM-as-reranker** | Prompt a general-purpose LLM to score or directly rank a list of documents ("Given this query, order these documents by relevance"). Can use pointwise scoring, pairwise comparisons, or listwise prompting (e.g. RankGPT-style). | GPT-4/Claude-based rerank pipelines |
| **Late-interaction models** | A middle ground between bi- and cross-encoders: encode query and doc tokens separately (so doc embeddings are still precomputable) but delay the interaction to a fine-grained token-level matching step (e.g. MaxSim) instead of collapsing to one vector. Faster than full cross-encoders, more accurate than plain bi-encoders. | ColBERT, ColBERTv2 |
| **Feature-based / learning-to-rank (LTR)** | Classical ML (gradient-boosted trees, e.g. LambdaMART) over hand-engineered features (BM25 score, click-through rate, recency, price, embedding similarity, etc.) rather than raw text. Common in production e-commerce/search when interpretability and low latency matter, or when combining many heterogeneous signals beyond just text relevance. | LightGBM/XGBoost LambdaMART rankers |
| **Reciprocal Rank Fusion (RRF)** | Not a learned model — a simple rule-based way to merge rankings from multiple retrievers (e.g. lexical + vector search) before/instead of a learned reranker. Often used as a cheap baseline or combined with a learned reranker. | RRF in hybrid search stacks |

---

## 7. Where reranking fits in different systems

### Search engines
`Query → BM25/vector retrieval (top 200) → cross-encoder rerank (top 20) → display to user`

### RAG (Retrieval-Augmented Generation) pipelines
```
Query → embed + vector search (top 20-50 chunks)
       → rerank (top 3-5 chunks)
       → stuff into LLM prompt as context
       → LLM generates answer
```
Reranking here is critical: LLM context windows are limited and "lost in the middle" effects mean irrelevant chunks dilute answer quality — reranking ensures only the truly best chunks make it into the prompt.

### Recommendation systems (relevant to a `recommendation-service`-style architecture)
```
User → candidate generation (collaborative filtering / embeddings / popularity,
        producing hundreds of candidate items, optimized for recall)
     → ranking/reranking model (scores each candidate using rich features:
        user embedding, item embedding, user-item interaction history,
        context — time of day, device, session — business rules, diversity,
        freshness/recency, exploration)
     → final ranked feed shown to user
```
In recommendation systems specifically, the "reranker" is often a supervised model (e.g. a gradient-boosted tree or a neural network, sometimes a two-tower model refined by a cross-attention layer) trained on implicit feedback (clicks, dwell time, conversions, likes) rather than a text cross-encoder — but the architectural motivation is identical: cheap candidate generation for recall, followed by an expensive, high-precision model to get the final order right. Business logic (diversity, deduplication, freshness boosting, business rule filters) is also frequently layered in at this reranking stage, since it's the last point before results are shown to the user.

---

## 8. Practical trade-offs and considerations

- **Latency budget:** reranking adds latency proportional to `(number of candidates) × (per-pair inference cost)`. Systems tune the candidate-set size (e.g. rerank top 100 instead of top 1000) to hit latency SLAs.
- **Batching:** cross-encoder inference is embarrassingly parallel across candidates for a single query, so GPU batching dramatically reduces wall-clock latency.
- **Model size vs. quality:** smaller distilled rerankers (e.g. MiniLM-based) trade a bit of accuracy for much lower latency/cost; larger rerankers (or LLM-based rerankers) squeeze out more quality at higher cost — a classic accuracy/latency/cost trade-off to tune per use case.
- **Truncation:** cross-encoders have a max sequence length (often 512 tokens); very long documents must be chunked or truncated, which can lose relevant information if not handled carefully.
- **Combining with business signals:** in production, the final rank often isn't *purely* the reranker's relevance score — it's blended with other signals (popularity, freshness, price, personalization, diversity constraints, A/B test variants) via a weighted formula or a second, small learned model.
- **Caching:** unlike bi-encoder document embeddings, cross-encoder scores generally can't be cached across queries (they're query-specific), so reranking cost is paid on every request — another reason candidate sets are kept small.

---

## 9. Summary

A reranker is the **second, precision-focused stage** of a two-stage ranking pipeline. It exists because:

1. The most accurate relevance-scoring architecture — a **cross-encoder**, which lets query and document attend to each other jointly — is too computationally expensive to run over an entire corpus.
2. A cheaper **bi-encoder** (or lexical search) is used first to shrink millions of candidates down to a manageable shortlist, prioritizing recall.
3. The reranker then applies its more expensive, more accurate scoring to just that shortlist, prioritizing precision, and produces the final order shown to the user (or passed to a downstream LLM/application).

The same "cheap-recall, then expensive-precision" pattern shows up under different names across search, RAG, and recommendation systems — the reranker is simply the "expensive-precision" half of that pattern, whatever specific model architecture (cross-encoder, late-interaction, LLM, or feature-based LTR model) is used to implement it.
