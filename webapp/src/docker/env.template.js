(function (window) {
    window.__env = window.__env || {};
    window.__env.apiBaseUrl = "http://localhost:8085";
    window.__env.appThemeColor = "orange";

    // ✅ Apply theme color to CSS variable
    document.documentElement.style.setProperty(
        "--app-theme-color",
        window.__env.appThemeColor
    );

})(this);