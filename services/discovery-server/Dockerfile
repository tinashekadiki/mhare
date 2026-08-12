FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY libraries ./libraries
COPY services ./services
RUN mvn -pl services/discovery-server -am -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre

RUN useradd --create-home --uid 10001 emhare
USER emhare
WORKDIR /app
COPY --from=build /workspace/services/discovery-server/target/discovery-server-*.jar /app/discovery-server.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/discovery-server.jar"]
