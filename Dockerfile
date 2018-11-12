FROM openjdk:11-oracle

WORKDIR /app

ADD ./build/libs/simple-microservice.jar .
ADD ./api-responses ./api-responses
RUN mkdir logs

ENV LANG en_US.UTF-8

EXPOSE 80

# Start app with JFR listening on port 80
CMD [ "java", \
    "-XX:+FlightRecorder", \
    "-XX:StartFlightRecording=disk=true,filename=./logs/sm.jfr,dumponexit=true,maxage=1d", \
    "-Xmx4m", "-jar", "simple-microservice.jar", "80" ]
