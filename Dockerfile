FROM clojure:openjdk-11-tools-deps-slim-buster

ENV \
  DEBCONF_NONINTERACTIVE_SEEN=true \
  DEBIAN_FRONTEND="noninteractive"

WORKDIR /app

RUN \
  apt-get -qq update \
  && apt-get install -qy \
    ca-certificates \
    tini \
    tzdata \
  && \
  apt-get remove -y jq \
  && apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false \
  && apt-get autoremove -y \
  && apt-get clean \
  && \
  rm -rf \
    /tmp/* \
    /var/lib/apt/lists/* \
    /var/cache/apt/* \
    /var/tmp/*

COPY . /app
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java","-jar","/app/target/Doplarr.jar"]

LABEL "maintainer"="Kiran Shila <me@kiranshila.com>"
LABEL "org.opencontainers.image.source"="https://github.com/kiranshila/Doplarr"
