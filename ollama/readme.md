
# Run ollama in local container
```
docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama
curl -v http://localhost:11434/api/version

docker exec -it ollama ollama pull qwen:0.5b
curl -X POST http://localhost:11434/api/generate -d '{
  "model": "phi3",
  "prompt":"what is phi3?"
 }'
curl http://localhost:11434/api/chat -d '{
  "model": "phi3",
  "messages": [
    { "role": "user", "content": "why is the sky blue?" }
  ]
}'
```
