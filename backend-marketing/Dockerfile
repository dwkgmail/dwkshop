FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-marketing/pom.xml backend-marketing/pom.xml
COPY backend-marketing/src backend-marketing/src
RUN mvn -q -f backend-marketing/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-marketing/target/*.jar app.jar
EXPOSE 18087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
