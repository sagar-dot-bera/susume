FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Create directory for model artifacts
RUN mkdir -p models

ENV MODEL_PATH=/app/models/susume_ranker_v1.joblib \
    MODEL_VERSION=v1 \
    LOG_LEVEL=INFO \
    SERVICE_PORT=8002

EXPOSE 8002

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
