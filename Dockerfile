FROM python:3.12-slim

WORKDIR /app

RUN set -eux; \
    unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY all_proxy ALL_PROXY; \
    printf '%s\n' 'Acquire::http::Proxy "false";' 'Acquire::https::Proxy "false";' > /etc/apt/apt.conf.d/99no-proxy; \
    (test -f /etc/apt/sources.list && sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' /etc/apt/sources.list) || true; \
    (test -f /etc/apt/sources.list.d/debian.sources && sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' /etc/apt/sources.list.d/debian.sources) || true; \
    apt-get update; \
    apt-get install -y gcc; \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN set -eux; \
    unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY all_proxy ALL_PROXY; \
    pip install --no-cache-dir -r requirements.txt

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
