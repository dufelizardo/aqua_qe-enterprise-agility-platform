# API do AQuA-QE (Spring Boot). O build usa a imagem oficial do Maven (sem baixar o Maven pelo wrapper, que falhou
# por instabilidade do repositório central); a imagem final só tem o JRE.
FROM maven:3.9.11-eclipse-temurin-25-alpine AS build
WORKDIR /src
COPY pom.xml ./
COPY src src
# Retentativas do transporte HTTP do Maven para downloads de dependências (rede instável).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
      -Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=120000

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /src/target/aqua-qe-platform-*.jar /tmp/app.jar
# Extrai o jar em camadas: o Java carrega mais rápido, o que importa num servidor modesto.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar \
 && addgroup -S app && adduser -S -G app app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
