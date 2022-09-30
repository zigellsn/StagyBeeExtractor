FROM gradle:7.5.1-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle installDist --no-daemon

FROM openjdk:13-jre-slim

ENV EXTRACTOR_VERSION 1.0.17
ENV KTOR_USER ktor
ENV HOME /home/$KTOR_USER
RUN useradd --create-home $KTOR_USER && \
    mkdir $HOME/app && \
    chown -R $KTOR_USER $HOME/app

USER $KTOR_USER

COPY --from=build /home/gradle/src/build/install/StagyBeeExtractor/ $HOME/app/
EXPOSE 8443

WORKDIR $HOME/app/bin

ENTRYPOINT [ "./StagyBeeExtractor" ]
