# ---- Build stage ----
# Maven をローカルに用意しなくてもイメージ内でビルドできるよう、公式 maven イメージを使う。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 依存解決を先にキャッシュ(pom だけ先にコピー)。
# go-offline はキャッシュ温めなので、失敗しても後続の package で再解決する。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

# ソースをコピーしてパッケージング(テストはコンテナビルド時はスキップして高速化)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
