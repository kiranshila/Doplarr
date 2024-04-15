FROM clojure:tools-deps-1.11.1.1435 as builder

WORKDIR /src

COPY  ./deps.edn ./config.edn ./
COPY ./build/ /src/build
COPY ./src/ /src/src

RUN clojure -P
RUN clojure -T:build uber

ENTRYPOINT [ "sh" ]

FROM eclipse-temurin:22_36-jre-alpine as runtime

WORKDIR /app

RUN \
  apk add --no-cache \
    ca-certificates \
    tini \
    tzdata

COPY --from=builder /src/target/doplarr.jar .
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["java","-jar","/app/doplarr.jar"]

LABEL "maintainer"="Kiran Shila <me@kiranshila.com>"
LABEL "org.opencontainers.image.source"="https://github.com/kiranshila/Doplarr"
