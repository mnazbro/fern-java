FROM eclipse-temurin:11-jre-alpine

ARG BUILD_VERSION
ENV VERSION=$BUILD_VERSION
COPY cli-$BUILD_VERSION.tar init.sh /
RUN mv cli-$BUILD_VERSION.tar cli.tar

ENTRYPOINT ["bin/sh", "init.sh"]
