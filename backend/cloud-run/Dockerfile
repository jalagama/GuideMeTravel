FROM node:20-slim

WORKDIR /app

COPY functions/package*.json ./functions/
COPY functions/tsconfig.json ./functions/
COPY functions/src ./functions/src
RUN cd functions && npm install --cache ./.npm-cache && npm run build

COPY cloud-run/package*.json ./cloud-run/
COPY cloud-run/tsconfig.json ./cloud-run/
COPY cloud-run/src ./cloud-run/src
RUN cd cloud-run && npm install --cache ./.npm-cache && npm run build

ENV PORT=8080
EXPOSE 8080

CMD ["node", "cloud-run/lib/server.js"]
