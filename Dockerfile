# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copia o POM primeiro e baixa as dependências em uma camada separada.
# Docker re-usa esta camada enquanto o pom.xml não mudar — builds subsequentes
# saltam o download de ~300 MB de dependências.
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

COPY src ./src
RUN mvn -q clean package -DskipTests

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# Imagem de runtime sem JDK — reduz surface de ataque e tamanho final (~250 MB vs ~600 MB)
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuário não-root obrigatório em ambientes de produção com PodSecurityPolicy
RUN addgroup -S notifyhub && adduser -S notifyhub -G notifyhub
WORKDIR /app

COPY --from=builder /build/target/notify-hub-*.jar app.jar
RUN chown notifyhub:notifyhub app.jar

USER notifyhub
EXPOSE 8080

# -XX:+UseContainerSupport: JVM respeita os limites de CPU/RAM do container (cgroups v2)
# -XX:MaxRAMPercentage=75.0: reserva 25% para GC overhead e stack nativo
# -Djava.security.egd: fonte de entropia mais rápida — evita startup lento em /dev/random
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
