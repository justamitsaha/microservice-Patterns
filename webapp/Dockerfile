FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
# Important: ensure --base-href is correct
RUN npm run build --prod -- --base-href=/web/

FROM nginx:1.25-alpine

# Copy Angular build to nginx document root
COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

# Copy nginx configuration
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# Copy runtime env template
COPY docker/env.template.js /usr/share/nginx/html/assets/env.template.js

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]


# docker run -p 8080:80 \
#   -e API_BASE_URL="http://localhost:9000" \
#   -e APP_THEME_COLOR="#2196f3" \
#   my-webapp:local

