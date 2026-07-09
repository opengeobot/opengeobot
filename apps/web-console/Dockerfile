# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web-console/ ./apps/web-console/
RUN pnpm install --frozen-lockfile
RUN cd apps/web-console && pnpm build

# Serve stage
FROM nginx:1.27-alpine
COPY --from=builder /app/apps/web-console/dist /usr/share/nginx/html
COPY apps/web-console/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
