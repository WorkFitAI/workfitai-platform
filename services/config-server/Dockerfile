# ===============================
# Stage 1: Build jar bằng Maven
# ===============================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy file cấu hình Maven và tải dependencies trước (tận dụng cache)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ source và build jar
COPY src ./src
RUN mvn clean package -DskipTests

# ===============================
# Stage 2: Runtime image nhẹ
# ===============================
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy file jar từ stage builder sang runtime
COPY --from=builder /app/target/*.jar app.jar

# Mở port cho ứng dụng (Config Server mặc định là 8888)
EXPOSE 8888

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]