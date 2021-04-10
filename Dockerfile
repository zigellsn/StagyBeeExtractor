FROM gradle:7.0-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

FROM openjdk:13-jre-slim

ENV EXTRACTOR_VERSION 1.0.8
ENV KTOR_USER ktor
ENV HOME /home/$KTOR_USER
RUN useradd --create-home $KTOR_USER && \
    mkdir $HOME/app && \
    chown -R $KTOR_USER $HOME/app

WORKDIR $HOME/app
USER $KTOR_USER

COPY --from=build /home/gradle/src/build/libs/*.jar ./
EXPOSE 8080
EXPOSE 9090

COPY ./scripts/entrypoint.sh $HOME/
ENTRYPOINT [ "/home/ktor/entrypoint.sh" ]
