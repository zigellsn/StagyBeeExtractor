FROM gradle:6.1.1-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

FROM openjdk:8-jre-slim

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

ENTRYPOINT [ "java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:InitialRAMFraction=2", "-XX:MinRAMFraction=2", "-XX:MaxRAMFraction=2", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "StagyBeeExtractor.jar" ]
