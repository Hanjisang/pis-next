FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN sh ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/pis-next-backend-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
