FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-product/pom.xml backend-product/pom.xml
COPY backend-product/src backend-product/src
RUN mvn -q -f backend-product/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend-product/target/*.jar app.jar
EXPOSE 18082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
