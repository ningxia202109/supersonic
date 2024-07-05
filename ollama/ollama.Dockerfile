FROM ollama/ollama
COPY init-ollama.sh /init-ollama.sh
RUN chmod +x /init-ollama.sh
CMD ["/init-ollama.sh"]
