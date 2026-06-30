FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-cart/pom.xml backend-cart/pom.xml
COPY backend-cart/src backend-cart/src
RUN mvn -q -f backend-cart/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-cart/target/*.jar app.jar
EXPOSE 18083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
