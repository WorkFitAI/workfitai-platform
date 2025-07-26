# Build stage
FROM maven:latest AS builder
WORKDIR /app
COPY pom.xml /app/
COPY src /app/src
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM openjdk:21-jdk
WORKDIR /app

# Copy jar artifact từ builder
COPY --from=builder /app/target/*.jar /app/app.jar

# Mở port 8080
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java","-jar","/app.jar"]