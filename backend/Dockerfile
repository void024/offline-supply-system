# Two-stage build: Maven + JDK 21 to build, JRE 21 to run (Prompt 4 rules).
# NOTE: written but NOT executed in the authoring sandbox (no Docker daemon there);
# verified locally via plain `mvn package` + `java -jar` instead.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/supply-server-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
