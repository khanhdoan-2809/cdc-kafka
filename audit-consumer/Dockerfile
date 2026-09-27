FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder \
    /app/target/audit-consumer-*.jar \
    /app/audit-consumer.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/audit-consumer.jar"]