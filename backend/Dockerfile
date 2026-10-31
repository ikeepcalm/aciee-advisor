FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN useradd --system app
USER app
COPY --from=build /src/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
