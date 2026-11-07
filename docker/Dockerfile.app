# Contexto de construcción: raíz del repositorio.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/ ./src/main/resources/static/
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
ARG GIT_SHA=desconocido
ENV COMMIT_SHA=$GIT_SHA
RUN useradd -r -u 10001 servilink
USER servilink
COPY --from=api /api/target/servilink-backend-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
