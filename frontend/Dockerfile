# Estágio 1: Build (Compilação do Angular)
FROM node:24-alpine AS build
WORKDIR /app

# Copia os arquivos de dependência e instala
COPY package*.json ./
RUN npm install

# Copia o restante do código e faz o build de produção
COPY . .
RUN npm run build -- --configuration production

# Estágio 2: Serve (Servidor Nginx interno para o Angular)
FROM nginx:alpine

# Remove a página padrão do Nginx
RUN rm -rf /usr/share/nginx/html/*

# Copia os arquivos gerados pelo Angular no Estágio 1
# NOTA: O Angular moderno gera o build dentro de "dist/<nome-do-projeto>/browser".
# Como o projeto foi criado com o nome "frontend", o caminho será "dist/frontend/browser".
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copia a configuração customizada do Nginx para suportar o roteamento do Angular (SPA)
COPY nginx-custom.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
