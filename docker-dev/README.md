# Start SuperSonic with multiple containers
Setup multiple container for supersonic development, All dependency services execute in multiple containers. Dependency services includes: 
- Chrome
- Mysql
- Ollama

## Remote container is codespaces 
```
mkdir .devcontainer
cp codespace-devcontainer.json .devcontainer/devcontainer.json
```
## Start dependency services in docker-compose
```
docker-compose -f build
docker-compose up -d
```

## Test Ollama
```
# Check Ollama version
curl -v http://localhost:11434/api/version

# prompt for llm-model qwen:0.5b https://ollama.com/library/qwen2:0.5b
curl -X POST http://localhost:11434/api/generate \
-d '{"model": "qwen:0.5b","prompt":"what is qwen?"}'
```
