FROM maven:3.9.12-eclipse-temurin-25 AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 --create-home devs
COPY --from=builder --chown=devs:devs /workspace/target/devs-service-*.jar /app/devs-service.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/devs-service.jar"]
