FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-migrator/pom.xml backend-migrator/pom.xml
COPY backend-migrator/src backend-migrator/src
RUN mvn -q -f backend-migrator/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-migrator/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
