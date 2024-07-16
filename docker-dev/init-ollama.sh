#!/bin/bash
/bin/ollama serve &

# Wait a little for Ollama to fully start
sleep 30

# Use environment variable for model name
MODEL_NAME=${LLM_MODEL}

# Check if the model has already been pulled
if ! ollama list | grep -q "${MODEL_NAME}"; then
  echo "Pulling ${MODEL_NAME} model..."
  ollama pull ${MODEL_NAME}
else
  echo "${MODEL_NAME} model already pulled. Skipping..."
fi

sleep infinity
