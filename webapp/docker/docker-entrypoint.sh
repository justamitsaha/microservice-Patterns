#!/bin/sh

echo "Injecting runtime environment variables..."

sed -e "s|\$API_BASE_URL|${API_BASE_URL}|g" \
    -e "s|\$APP_THEME_COLOR|${APP_THEME_COLOR}|g" \
    /usr/share/nginx/html/assets/env.template.js \
    > /usr/share/nginx/html/assets/env.js

echo "Starting Nginx..."
nginx -g "daemon off;"
