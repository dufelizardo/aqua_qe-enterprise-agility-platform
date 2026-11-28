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
# Correções de segurança do Alpine publicadas depois da imagem base (#18; doc/SECURITY.md, seção 4).
RUN apk upgrade --no-cache
WORKDIR /app
COPY --from=build /src/target/aqua-qe-platform-*.jar /tmp/app.jar
# Extrai o jar em camadas: o Java carrega mais rápido, o que importa num servidor modesto.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar \
 && addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
# UID numérico: o Kubernetes só aceita runAsNonRoot quando consegue comprovar que o usuário não é root.
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
