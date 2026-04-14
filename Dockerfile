FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./gradlew
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# PORT is injected by Railway at runtime — no fixed EXPOSE needed
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
