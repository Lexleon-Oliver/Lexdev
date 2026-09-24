# ============================================================
# Estágio 1: Build (Compilação)
# ============================================================

FROM maven:3.9-eclipse-temurin-26 AS build

WORKDIR /app

# Copia o pom.xml primeiro para aproveitar o cache
COPY pom.xml .

RUN mvn dependency:go-offline -B

# Copia o código-fonte
COPY src ./src

# Compila a aplicação
RUN mvn clean package -DskipTests


# ============================================================
# Estágio 2: Runtime (Execução)
# ============================================================

FROM eclipse-temurin:26-jdk-alpine

WORKDIR /app

# Cria grupo e usuário não-root com UID/GID fixos
RUN addgroup -S -g 101 spring \
    && adduser -S -D -H -u 100 -G spring spring

# Prepara o diretório para armazenamento das imagens
RUN mkdir -p /data/product-images \
    && chown spring:spring /data/product-images \
    && chmod 770 /data/product-images

# Copia o JAR compilado no estágio build
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

# Executa a aplicação sem privilégios de root
USER 100:101

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]