# syntax=docker/dockerfile:1
# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache the dependency download layer separately from source changes
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

COPY lombok.config .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B -q

# ── Stage 2: Extract layered jar for optimal layer caching ────────────────────
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /build/target/onemep-feature-service.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: Minimal runtime image ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=extractor /app/dependencies/          ./
COPY --from=extractor /app/spring-boot-loader/    ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/           ./

USER spring

EXPOSE 8086

ENV SERVER_SERVLET_CONTEXT_PATH=/feature-service \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8086/feature-service/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
