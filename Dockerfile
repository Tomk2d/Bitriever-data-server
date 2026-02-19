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
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
