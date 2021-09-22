FROM adoptopenjdk/openjdk16:alpine-jre

WORKDIR /app

RUN \
  apk add --no-cache \
    ca-certificates \
    tini \
    tzdata

COPY . /app
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["java","-jar","/app/doplarr.jar"]

LABEL "maintainer"="Kiran Shila <me@kiranshila.com>"
LABEL "org.opencontainers.image.source"="https://github.com/kiranshila/Doplarr"
