# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml /app/
COPY src /app/src
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy jar artifact từ builder
COPY --from=builder /app/target/*.jar /app.jar

# Mở port 8088
EXPOSE 8088

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "/app.jar"]
