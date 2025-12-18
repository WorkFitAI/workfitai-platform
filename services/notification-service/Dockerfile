# Runtime-only stage: sử dụng JAR đã build sẵn từ máy local
FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy jar đã build sẵn từ target/ folder (chạy mvn clean install trước)
COPY ./target/*.jar /app/app.jar

# Mở port 9007
EXPOSE 9007

# Chạy ứng dụng
ENTRYPOINT ["java","-jar","/app/app.jar"]
