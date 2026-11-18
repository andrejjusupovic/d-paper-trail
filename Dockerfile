# T004 [NFR] Multi-stage build: Maven builds the jar, a slim JRE image runs it.

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the POM first so dependency downloads are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI / `mvn verify` (they need Docker for Testcontainers), not inside the image build
RUN mvn -B -q -DskipTests package

# ---- Stage 2: run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/target/papertrail.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
