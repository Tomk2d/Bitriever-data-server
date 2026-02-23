# Build stage — wrapper 없이 Gradle 이미지로 빌드
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN gradle bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:17-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && curl -sL -o opentelemetry-javaagent.jar https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.25.0/opentelemetry-javaagent-2.25.0.jar \
    && apt-get purge -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
