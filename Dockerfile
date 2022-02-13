FROM openjdk:latest

SHELL ["/bin/bash", "-c"]

WORKDIR /app

COPY . ./

EXPOSE 8080 8088

ENTRYPOINT java -cp bin server.JsonServer
