# Build a partir da raiz do monorepo (ADR-0002): docker build -f services/accounts/Dockerfile .
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY services/accounts/pom.xml services/accounts/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f services/accounts/pom.xml dependency:go-offline
COPY services/accounts/src services/accounts/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f services/accounts/pom.xml -DskipTests package \
    && cp services/accounts/target/accounts-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25.0.1_8-jre-alpine
RUN addgroup -S kipay && adduser -S kipay -G kipay
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER kipay
ENTRYPOINT ["java", "-jar", "/app/app.jar"]