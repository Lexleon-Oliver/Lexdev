# Estágio 1: Build (Compilação do código)
# Utiliza a imagem oficial do Maven com Eclipse Temurin para Java 26
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app

# Copia apenas o pom.xml primeiro para otimizar o cache do Docker (evita baixar tudo de novo se o código mudar)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e realiza o build, ignorando os testes para ser mais rápido na subida
COPY src ./src
RUN mvn clean package -DskipTests

# Estágio 2: Run (Execução)
# Utiliza uma imagem alpine (muito mais leve) apenas com o JRE/JDK do Java 26
FROM eclipse-temurin:26-jdk-alpine
WORKDIR /app

# Cria um usuário não-root por questões de segurança (Boas práticas de Docker)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia o .jar gerado no Estágio 1
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Comando para iniciar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]