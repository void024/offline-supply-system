# Combined single-service build: React frontend + Spring Boot backend + JRE runtime.
# Stage 1: build the frontend in real (non-demo) mode against the same-origin API.
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ENV VITE_DEMO_MODE=false VITE_API_BASE_URL=/api/v1
RUN npm run build

# Stage 2: build the backend, embedding the frontend build as static resources.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

# Stage 3: runtime. Heap capped for small free-tier instances; override via JAVA_TOOL_OPTIONS.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/supply-server-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx350m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
