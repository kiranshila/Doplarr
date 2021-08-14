FROM clojure:openjdk-11-tools-deps
MAINTAINER Kiran Shila <me@kiranshila.com>
COPY . /home/Doplarr
CMD ["java","-jar","/home/Doplarr/target/Doplarr.jar"]
