FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-aftersale/pom.xml backend-aftersale/pom.xml
COPY backend-aftersale/src backend-aftersale/src
RUN mvn -q -f backend-aftersale/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend-aftersale/target/*.jar app.jar
EXPOSE 18085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
