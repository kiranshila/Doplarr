FROM adoptopenjdk/openjdk11:alpine-jre
MAINTAINER Kiran Shila <me@kiranshila.com>
COPY target/Doplarr.jar /home/Doplarr.jar
CMD ["java","-jar","/home/Doplarr.jar"]
