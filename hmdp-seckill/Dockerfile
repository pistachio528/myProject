# 构建阶段
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# 先下载依赖（利用缓存层）
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
