FROM openjdk:11-oracle

WORKDIR /app

ADD ./build/libs/simple-microservice.jar .
ADD ./api-responses ./api-responses
RUN mkdir logs

EXPOSE 80

# Start app with JFR listening on port 80
CMD [ "java", \
    "-XX:+FlightRecorder", \
    "-XX:StartFlightRecording=disk=true,maxage=1d,filename=./logs/sm.jfr,dumponexit=true", \
    "-Xmx6m", "-jar", "simple-microservice.jar", "80" ]
