FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

## ==================================================

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && \
    useradd --system --gid spring spring

COPY --from=builder /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/audit-consumer.jar"]