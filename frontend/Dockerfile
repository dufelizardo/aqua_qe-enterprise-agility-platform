# Frontend do AQuA-QE: build do Angular e nginx servindo o SPA e repassando a API.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund --fetch-retries=5 --fetch-retry-mintimeout=20000
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.29-alpine
# Correções de segurança do Alpine publicadas depois da imagem base (#18; doc/SECURITY.md, seção 4).
RUN apk upgrade --no-cache
# O entrypoint oficial do nginx gera /etc/nginx/conf.d/default.conf a partir do template, com as variáveis de ambiente.
ENV BACKEND_URL=http://app
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
