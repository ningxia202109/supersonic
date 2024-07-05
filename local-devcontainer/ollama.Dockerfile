FROM ollama/ollama

# Set the working directory
WORKDIR /workspace

# Install curl
RUN apt-get update && apt-get install -y curl

# Copy the script
COPY init-ollama.sh init-ollama.sh
RUN chmod +x init-ollama.sh

# Reset the entrypoint to the default shell
ENTRYPOINT ["/bin/sh", "-c"]

# Execute the script
CMD ["./init-ollama.sh"]
