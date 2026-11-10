# Builds the ChestSorter plugin jar without installing a JDK locally.
#   podman build -t chestsorter-build --build-arg VER=2.1.1 .
#   id=$(podman create chestsorter-build x) && podman cp "$id:/" out && podman rm "$id"
#     -> out/ChestSorter-<version>.jar
# (Podman on Windows/macOS runs in remote mode, where "podman build -o" is not supported.)
# Runs the full Gradle build (tests, checkstyle, spotbugs); the final image holds only the jar.
FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
ARG VER=
# A Windows checkout (core.autocrlf) gives gradlew CRLF line endings, which sh cannot run.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# The cache mount keeps downloaded Gradle and dependencies between builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon build ${VER:+-Pver=$VER} \
    && mkdir /out && cp build/libs/*.jar /out/

FROM scratch
COPY --from=build /out/ /
