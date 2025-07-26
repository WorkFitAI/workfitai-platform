# --- STAGE 1: BUILD WITH MAVEN ---
FROM maven:3.8.6-openjdk-17 AS builder
WORKDIR /workspace

# copy only pom and wrapper to cache dependencies
COPY .mvn/       .mvn/
COPY mvnw        mvnw
COPY pom.xml     pom.xml

# copy source code
COPY src ./src

# package without running tests
RUN chmod +x mvnw && \
    ./mvnw clean package -DskipTests

# --- STAGE 2: RUN THE JAR ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# copy the fat-jar from builder
ARG JAR_FILE=target/*.jar
COPY --from=builder /workspace/${JAR_FILE} app.jar

# expose the default Spring port
EXPOSE 8080

# drop privileges (in case add USER later)
ENTRYPOINT ["java","-jar","/app/app.jar"]
