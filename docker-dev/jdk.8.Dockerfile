FROM ubuntu:22.04

# Install Docker
RUN apt-get update && \
    apt-get install -y zip unzip git apt-transport-https ca-certificates curl software-properties-common && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add - && \
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" && \
    apt-get update && \
    apt-get install -y docker-ce docker-ce-cli containerd.io && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN curl -s "https://get.sdkman.io" | bash && \
    bash -c "source \"$HOME/.sdkman/bin/sdkman-init.sh\" && \
    sdk list java && \
    sdk install java 8.0.412-amzn && \
    sdk install maven 3.8.4 && \
    java -version"

EXPOSE 9080

# Set the working directory
WORKDIR /workspace

# Define default command
CMD ["/bin/bash"]
