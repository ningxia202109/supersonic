# Use the official Node.js 20 image as the base image
FROM node:20

WORKDIR /workspace

EXPOSE 9000

CMD ["sh", "-c", "cd ./webapp && ./start-fe-dev.sh"]