# ----------------------------------------------------------------------------
# Schema Catalog Aggregator — backend (Quarkus, JVM)
#
# Стадии: build (Maven + JDK 25) -> runtime (JRE 25, non-root)
# ----------------------------------------------------------------------------

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------------------------------------------------------------------------

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

# curl — для healthcheck (docker compose / оркестраторы)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/quarkus-app/ ./

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
    CMD curl -f http://localhost:8080/q/health/live || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app/quarkus-run.jar"]