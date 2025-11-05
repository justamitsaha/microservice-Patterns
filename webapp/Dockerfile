# ---------------------------
# Step 1: Build Angular App
# ---------------------------
FROM node:20-alpine AS build

WORKDIR /app

# Install dependencies first (better caching)
COPY package*.json ./
RUN npm install

# Copy all source code
COPY . .

# Build Angular for production
RUN npm run build --prod

# ---------------------------
# Step 2: Run with Nginx
# ---------------------------
FROM nginx:1.25-alpine

# Copy only browser output to Nginx root
COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

# Copy env template
COPY docker/env.template.js /usr/share/nginx/html/assets/env.template.js

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
