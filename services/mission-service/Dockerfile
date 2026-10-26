FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src/main ./src/main
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system skygrid && useradd --system --gid skygrid --uid 10001 --no-create-home skygrid
COPY --from=build --chown=10001:10001 /workspace/target/mission-service-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
