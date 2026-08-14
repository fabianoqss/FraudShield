# ---- estágio 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copia só o necessário para baixar dependências primeiro (cache de camadas do Docker:
# se o pom.xml não mudar, o Docker reaproveita essa camada e pula o download de novo)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Só agora copia o código-fonte e compila
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- estágio 2: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Roda como usuário não-root (boa prática de segurança em containers)
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/target/*.jar app.jar
USER spring

EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
