FROM python:3.12-slim

WORKDIR /app

RUN set -eux; \
    if [ -f /etc/apt/sources.list ]; then \
      sed -i 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g; s|http://security.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g; s|https://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g; s|https://security.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list; \
    fi; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
      sed -i 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g; s|http://security.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g; s|https://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g; s|https://security.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list.d/debian.sources; \
    fi; \
    apt-get update; \
    apt-get install -y gcc; \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN set -eux; \
    python -m pip install --no-cache-dir --upgrade pip -i https://mirrors.aliyun.com/pypi/simple; \
    pip install --no-cache-dir --retries 10 --timeout 30 -i https://mirrors.aliyun.com/pypi/simple -r requirements.txt

COPY app ./app
COPY configs ./configs
COPY i18n ./i18n
COPY schemas ./schemas
COPY observability ./observability
COPY docs ./docs
COPY static ./static

RUN mkdir -p data

ENV OPEN_GEOBOT_STORAGE=postgres
ENV OPEN_GEOBOT_POSTGRES_DSN=postgresql://opengeo:opengeo_pass@db:5432/opengeobot

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
