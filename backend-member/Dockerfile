FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-common/pom.xml backend-common/pom.xml
COPY backend-common/src backend-common/src
RUN mvn -q -f backend-common/pom.xml -DskipTests install
COPY backend-member/pom.xml backend-member/pom.xml
COPY backend-member/src backend-member/src
RUN mvn -q -f backend-member/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-member/target/*.jar app.jar
EXPOSE 18086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
