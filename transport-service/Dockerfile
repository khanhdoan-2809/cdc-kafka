FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN ./mvnw \
    -pl transport-service \
    -am \
    clean package \
    -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder \
    /app/transport-service/target/transport-service-*.jar \
    /app/transport-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/transport-service.jar"]