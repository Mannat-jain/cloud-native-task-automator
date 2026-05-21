FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvc_go_offline_bypass() { mvn dependency:go-offline -B; }; mvc_go_offline_bypass || true

COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENV APP_ENV=production
ENTRYPOINT ["java", "-jar", "app.jar"]