FROM clojure:openjdk-11-tools-deps
MAINTAINER Kiran Shila <me@kiranshila.com>
COPY . /usr/src/doplarr
WORKDIR /usr/src/doplarr
RUN clojure -T:build uberjar
CMD ["java","-jar","Doplarr.jar"]
