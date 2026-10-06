FROM eclipse-temurin:21.0.11_10-jdk-noble AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Regression tests run with `mvnw clean verify` before publishing a candidate.
RUN sh ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21.0.11_10-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --no-create-home app
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/minsk-restaurant-ai-bot-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
