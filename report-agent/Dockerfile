# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Cache dependency downloads separately from source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline --batch-mode -q

# Build the fat JAR (skills/ is bundled as a classpath resource via pom.xml)
COPY src/ src/
COPY skills/ skills/
RUN ./mvnw package -DskipTests --batch-mode -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

# Injected automatically by docker buildx for each platform (e.g. amd64, arm64)
ARG TARGETARCH

# Base utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        jq \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/target/report-agent-*.jar app.jar
COPY --from=builder /app/skills/ /app/skills/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
