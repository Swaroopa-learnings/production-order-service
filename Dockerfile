
# Stage 1: compile and package with Java 21
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B -DskipTests package

# Stage 2: run the built application
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/production-order-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

