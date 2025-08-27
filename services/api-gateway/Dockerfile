# =====================
# BUILD STAGE
# =====================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Build app using Maven
RUN mvn clean package -DskipTests

# =====================
# RUNTIME STAGE
# =====================
FROM eclipse-temurin:17-jdk

# Copy built jar from previous stage
COPY --from=build /app/target/api-gateway-*.jar app.jar

# Expose port (must match `server.port` in application.properties)
EXPOSE 8088

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
