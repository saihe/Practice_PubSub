# ---- deps ----
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* はビルド時にバンドルへ埋め込まれる。
# ブラウザはホスト側で動くため、バックエンドは localhost:8080 で到達する。
ARG NEXT_PUBLIC_API_ROOT=http://localhost:8080/api
ENV NEXT_PUBLIC_API_ROOT=$NEXT_PUBLIC_API_ROOT
RUN npm run build

# ---- run ----
FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/next.config.js ./next.config.js
EXPOSE 3000
CMD ["npm", "start"]
