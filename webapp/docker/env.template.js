(function (window) {
    window.__env = window.__env || {};
    window.__env.apiBaseUrl = "$API_BASE_URL";
    window.__env.appThemeColor = "$APP_THEME_COLOR";

    // ✅ Apply theme color to CSS variable
    document.documentElement.style.setProperty(
        "--app-theme-color",
        window.__env.appThemeColor
    );
})(this);
