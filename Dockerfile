FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y gcc && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY configs ./configs
COPY i18n ./i18n
COPY schemas ./schemas
COPY observability ./observability
COPY docs ./docs

RUN mkdir -p data

ENV OPEN_GEOBOT_STORAGE=postgres
ENV OPEN_GEOBOT_POSTGRES_DSN=postgresql://opengeo:opengeo_pass@db:5432/opengeobot

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
