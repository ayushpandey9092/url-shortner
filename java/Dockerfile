# Stage 1: build the jar with Maven and Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the build file first, so the downloaded libraries are cached between builds.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: a small runtime image that only has Java and our jar.
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the health check below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a normal user, not root. The data folder holds the SQLite database.
RUN useradd --create-home appuser && mkdir -p /app/data && chown appuser /app/data
USER appuser

COPY --from=build /app/target/url-shortner-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Docker checks /health every 30 seconds and marks the container unhealthy if it fails.
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD curl -fsS http://localhost:8080/health || exit 1

# Settings can be changed with environment variables, for example APP_BASE_URL.
CMD ["java", "-jar", "app.jar"]
