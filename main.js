   /* ---------------------------
       DARK MODE WITH STORAGE
    ----------------------------*/
    function toggleDarkMode() {
        document.body.classList.toggle("dark");
        localStorage.setItem("darkMode", document.body.classList.contains("dark"));
    }
    if (localStorage.getItem("darkMode") === "true") {
        document.body.classList.add("dark");
    }


    /* ---------------------------
       URL GENERATOR + SAVE HOST
    ----------------------------*/
    function generateUrls() {
        let host = document.getElementById("hostInput").value.trim();
        if (!host) return;

        // Auto-prefix http:// if missing
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }

        // save host to localStorage
        localStorage.setItem("savedHost", host);

        renderUrls(host);
    }

    // Render URLs based on host
    function renderUrls(host) {
        const cleanHost = host.replace(/\/+$/, "");

        const urls = {
            "Customer Service": `${cleanHost}/api/customer`,
            "Order Service": `${cleanHost}/api/order`,
            "Gateway": `${cleanHost}/gateway`,
            "Eureka Server": `${cleanHost}/eureka`,
            "Config Server Actuator": `${cleanHost}/config/actuator/health`,
            "Web App": `${cleanHost}/`
        };

        let html = "";

        for (const [name, url] of Object.entries(urls)) {
            html += `
                <div class="url-row">
                    <strong>${name}</strong>
                    <span class="url-value">${url}</span>
                    <a href="${url}" target="_blank">Open</a>
                    <button onclick="copySpanValue(this)">Copy</button>
                </div>
            `;
        }

        document.getElementById("generatedLinks").innerHTML = html;
    }

    // On page load: auto-fill saved host
    window.onload = () => {
        const savedHost = localStorage.getItem("savedHost");

        if (savedHost) {
            document.getElementById("hostInput").value = savedHost;
            renderUrls(savedHost);
        }
    };


    /* ---------------------------
       COPY FROM SPAN
    ----------------------------*/
    function copySpanValue(btn) {
        const text = btn.parentNode.querySelector("span").innerText.trim();
        navigator.clipboard.writeText(text);
    }


    /* ---------------------------
       COMMAND SEARCH
    ----------------------------*/
    function searchCommands() {
        const query = document.getElementById("cmdSearch").value.toLowerCase();
        const cmdSections = document.querySelectorAll(".cmd-row");

        cmdSections.forEach(row => {
            const content = row.innerText.toLowerCase();
            row.style.display = content.includes(query) ? "" : "none";
        });
    }