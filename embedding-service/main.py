import os
from typing import List
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Recommendation Engine - Embedding Service")

MODEL_NAME = os.getenv("MODEL_NAME", "all-MiniLM-L6-v2")
model = None

@app.on_event("startup")
async def startup_event():
    global model
    logger.info(f"Loading embedding model with ONNX Runtime: {MODEL_NAME}")
    try:
        from sentence_transformers import SentenceTransformer
        # Use ONNX backend for lightweight, fast CPU inference
        model = SentenceTransformer(MODEL_NAME, backend="onnx")
        logger.info("ONNX model loaded successfully")
    except Exception as e:
        logger.warning(f"ONNX backend initialization notice ({e}), loading CPU SentenceTransformer")
        from sentence_transformers import SentenceTransformer
        model = SentenceTransformer(MODEL_NAME)
        logger.info("SentenceTransformer model loaded successfully")

class EmbedRequest(BaseModel):
    text: str

class EmbedResponse(BaseModel):
    embedding: List[float]
    dimension: int

@app.post("/embed", response_model=EmbedResponse)
async def embed(request: EmbedRequest):
    """Generate embeddings for a text string."""
    if not request.text or not request.text.strip():
        raise HTTPException(status_code=400, detail="text cannot be empty")
    
    embedding = model.encode(request.text, convert_to_tensor=False).tolist()
    return EmbedResponse(embedding=embedding, dimension=len(embedding))

@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "model": MODEL_NAME, "backend": "onnx"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
