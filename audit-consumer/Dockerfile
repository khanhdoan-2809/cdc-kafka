FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN ./mvnw \
    -pl audit-consumer \
    -am \
    clean package \
    -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder \
    /app/audit-consumer/target/audit-consumer-*.jar \
    /app/audit-consumer.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/audit-consumer.jar"]