FROM gradle:7.4.2-jdk11-alpine

ARG BUILD_VERSION
ENV VERSION=$BUILD_VERSION
COPY model-generator-$BUILD_VERSION.tar init.sh /
RUN cd / \
    && mkdir -m777 /model-generator  \
    && mv /model-generator-$BUILD_VERSION.tar /model-generator/model-generator.tar \
    && cd model-generator \
    && tar -xvf model-generator.tar \
    && rm -rf model-generator.tar

ENTRYPOINT ["sh", "/init.sh"]