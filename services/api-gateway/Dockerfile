# =====================
# RUNTIME STAGE
# =====================
# Runtime-only stage: sử dụng JAR đã build sẵn từ máy local
FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy jar đã build sẵn từ target/ folder (chạy mvn clean install trước)
COPY ./target/api-gateway-*.jar /app/app.jar

# Expose port for API Gateway
EXPOSE 8080

# Run
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Expose port (must match `server.port` in application.properties)
EXPOSE 8088

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
