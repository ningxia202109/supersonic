FROM ubuntu:22.04

# Install necessary packages
RUN apt-get update && apt-get install -y curl zip unzip git ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Install SDKMAN, Java SDK 21, and verify Java installation in a single RUN command
RUN curl -s "https://get.sdkman.io" | bash && \
    bash -c "source \"$HOME/.sdkman/bin/sdkman-init.sh\" && \
    sdk list java && \
    sdk install java 8.0.412-amzn && \
    java -version"

EXPOSE 9080

# Set the working directory
WORKDIR /workspace
