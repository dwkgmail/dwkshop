FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-order/pom.xml backend-order/pom.xml
COPY backend-order/src backend-order/src
RUN mvn -q -f backend-order/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-order/target/*.jar app.jar
EXPOSE 18084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
