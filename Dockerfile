# API do AQuA-QE (Spring Boot). Build com o Maven Wrapper do projeto; imagem final só com o JRE.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /src/target/aqua-qe-platform-*.jar /tmp/app.jar
# Extrai o jar em camadas: o Java carrega mais rápido, o que importa num servidor modesto.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar \
 && addgroup -S app && adduser -S -G app app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
