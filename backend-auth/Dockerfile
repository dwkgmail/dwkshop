FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-auth/pom.xml backend-auth/pom.xml
COPY backend-auth/src backend-auth/src
RUN mvn -q -f backend-auth/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend-auth/target/*.jar app.jar
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
