FROM python:3.11-slim

WORKDIR /app

# Install CPU-only PyTorch (strips ~2.5GB CUDA binaries)
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 8001

CMD ["python", "main.py"]
