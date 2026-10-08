# Build a partir da raiz do monorepo (ADR-0002): docker build -f services/ledger/Dockerfile .
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY services/ledger/pom.xml services/ledger/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f services/ledger/pom.xml dependency:go-offline
COPY services/ledger/src services/ledger/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f services/ledger/pom.xml -DskipTests package \
    && cp services/ledger/target/ledger-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25.0.1_8-jre-alpine
RUN addgroup -S kipay && adduser -S kipay -G kipay
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER kipay
ENTRYPOINT ["java", "-jar", "/app/app.jar"]