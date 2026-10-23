FROM amazoncorretto:21 AS build

WORKDIR /app

RUN yum install -y tar gzip

COPY mvnw pom.xml ./

COPY .mvn/ .mvn/

RUN chmod +x mvnw

COPY /src /src

RUN ./mvnw clean package -DskipTests

from eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 3005

ENTRYPOINT ["java", "-jar","app.jar","-XX:MaxRAMPercentage=75.0"]