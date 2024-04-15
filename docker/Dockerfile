FROM clojure:tools-deps-1.11.1.1435 as builder

WORKDIR /src

# CACHING DEPS
COPY  ./deps.edn ./
RUN clojure -P
RUN clojure -T:build || true

COPY ./build/ /src/build/
COPY ./src/ /src/src/

RUN clojure -T:build uber

FROM eclipse-temurin:22_36-jre-alpine as runtime

WORKDIR /app

RUN \
  apk add --no-cache \
    ca-certificates \
    tini \
    tzdata

COPY --from=builder /src/target/doplarr.jar ./
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["java","-jar","/app/doplarr.jar"]

LABEL "maintainer"="Kiran Shila <me@kiranshila.com>"
LABEL "org.opencontainers.image.source"="https://github.com/kiranshila/Doplarr"
