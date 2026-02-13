# ---- Build Stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn -q -e -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests


# ---- Run Stage ----
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=builder /app/target/screen-agent-1.0.0.jar app.jar

# Create screenshot dir inside container
RUN mkdir /screenshots

EXPOSE 8085

ENTRYPOINT ["java","-jar","/app/app.jar"]
