---
description: Implement the core recommendation engine — weighted user vector construction, cosine similarity ranking, cold-start fallback with trending, and the GET /recommendations endpoints. This is the most complex workflow in the project.
---

# Workflow: Recommendation Engine

## Epics Covered
- E10: User Vector Construction
- E11: Cosine Similarity Ranking
- E12: Cold-Start / Trending Fallback
- E13: Recommendation API Endpoints

## User Stories

### US-12: Personalized Recommendations
**As a** tenant client application,
**I want to** get personalized item recommendations for a specific user,
**So that** I can surface relevant content to that user in my product.

**Acceptance Criteria:**
- `GET /api/v1/recommendations/{externalUserId}` (requires `X-API-KEY`)
- Optional query params: `?limit=10` (default 10, max = `MAX_RECOMMENDATION_LIMIT` env var)
- If user has **no interactions** → fall back to trending (see US-13)
- If user has interactions → run full algorithm (see algorithm below)
- Excluded from results:
  - Items the user has already interacted with (any type)
  - Items with `status=INACTIVE`
  - Items from other tenants
- Response HTTP 200:
  ```json
  {
    "userId": "string",
    "recommendations": [
      { "externalItemId": "string", "metadata": {}, "similarityScore": 0.92 }
    ],
    "strategy": "personalized | trending"
  }
  ```
- p95 response time target: < 500ms

---

### US-13: Trending Recommendations (Cold-Start Fallback)
**As a** tenant client,
**I want to** get trending items for new users with no history,
**So that** the recommendation surface is never empty.

**Acceptance Criteria:**
- `GET /api/v1/recommendations/trending` (requires `X-API-KEY`)
- Optional: `?limit=10`, `?days=30` (lookback window for interaction counting)
- Returns ACTIVE items ranked by aggregate interaction count in the past N days
- Items from all interaction types are counted (VIEW counts as 1, PURCHASE counts as 5 — weighted count)
- Response: same schema as US-12 but `"strategy": "trending"`

---

## Core Algorithm

### Step-by-step implementation in `RecommendationService`:

```
function getRecommendations(tenantId, externalUserId, limit):

  1. INTERACTION RETRIEVAL
     interactions = interactionRepository
       .findByTenantIdAndExternalUserId(tenantId, externalUserId)
     
     if interactions.isEmpty():
       return getTrending(tenantId, limit)

  2. EMBEDDING FETCH
     For each interaction in interactions:
       item = itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, interaction.externalItemId)
       if item != null:
         add (item.embedding, interaction.interactionType.weight) to weightedEmbeddings

  3. USER VECTOR COMPUTATION
     userVector = weightedAverage(weightedEmbeddings)
     
     weightedAverage(pairs):
       totalWeight = sum of all weights
       result = float[EMBEDDING_DIM] initialized to 0
       for each (embedding, weight) in pairs:
         result += embedding * weight
       return result / totalWeight

  4. CANDIDATE SET
     alreadyInteractedIds = Set of externalItemIds from interactions
     candidates = itemRepository.findByTenantIdAndStatus(tenantId, ACTIVE)
       .filter(item -> !alreadyInteractedIds.contains(item.externalItemId))

  5. COSINE SIMILARITY RANKING
     for each candidate in candidates:
       score = cosineSimilarity(userVector, candidate.embedding)
       scoredCandidates.add(candidate, score)
     
     scoredCandidates.sortByScoreDesc()

  6. RETURN TOP N
     return scoredCandidates.take(limit)
```

---

## Implementation Tasks

### 1. `VectorUtils` utility class

```java
public class VectorUtils {

    public static float[] weightedAverage(List<float[]> embeddings, List<Integer> weights) {
        int dim = embeddings.get(0).length;
        float[] result = new float[dim];
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();

        for (int i = 0; i < embeddings.size(); i++) {
            float w = (float) weights.get(i) / totalWeight;
            for (int d = 0; d < dim; d++) {
                result[d] += embeddings.get(i)[d] * w;
            }
        }
        return result;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

### 2. `RecommendationService`
- `getRecommendations(tenantId, externalUserId, limit)` — full algorithm
- `getTrending(tenantId, limit, days)` — aggregate weighted interaction counts

### 3. `TrendingRepository` (custom JPQL)
```java
@Query("""
  SELECT i.externalItemId, 
         SUM(CASE i.interactionType WHEN 'VIEW' THEN 1 WHEN 'CLICK' THEN 2 
             WHEN 'LIKE' THEN 3 WHEN 'PURCHASE' THEN 5 END) as score
  FROM Interaction i
  WHERE i.tenantId = :tenantId
    AND i.timestamp >= :since
  GROUP BY i.externalItemId
  ORDER BY score DESC
""")
List<Object[]> findTrendingItemIds(UUID tenantId, Instant since, Pageable p);
```

### 4. `RecommendationController`
- `GET /recommendations/{externalUserId}`
- `GET /recommendations/trending`

---

## Performance Checklist
- [ ] Model loaded once at embedding service startup (not per-request)
- [ ] Candidate embeddings fetched in a single DB query (not N+1)
- [ ] Similarity computation runs in-memory over candidate float arrays
- [ ] limit capped at `MAX_RECOMMENDATION_LIMIT` env var
- [ ] Response time validated < 500ms at p95 with realistic dataset
- [ ] Cold-start path tested: user with 0 interactions receives trending results
- [ ] Filter tested: previously interacted items do not appear in results
- [ ] Filter tested: INACTIVE items do not appear in results
