FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/ /app/
RUN mkdir -p /app/state
EXPOSE 8080
CMD ["java", "-jar", "/app/quarkus-run.jar"]
