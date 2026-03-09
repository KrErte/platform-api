(function() {
    'use strict';
    var TIMEOUT_MS = 15 * 60 * 1000;
    var WARNING_MS = 13 * 60 * 1000;
    var timer = null;
    var warningTimer = null;
    var modal = null;

    function resetTimers() {
        clearTimeout(timer);
        clearTimeout(warningTimer);
        hideWarning();
        warningTimer = setTimeout(showWarning, WARNING_MS);
        timer = setTimeout(doLogout, TIMEOUT_MS);
    }

    function doLogout() {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('userEmail');
        localStorage.removeItem('userName');
        sessionStorage.removeItem('vaultKey');
        window.location.href = '/login.html';
    }

    function showWarning() {
        if (!modal) createModal();
        modal.style.display = 'flex';
    }

    function hideWarning() {
        if (modal) modal.style.display = 'none';
    }

    function createModal() {
        var style = document.createElement('style');
        style.textContent =
            '.sg-overlay{position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:9999;}' +
            '.sg-box{background:#fff;border-radius:12px;padding:2rem;max-width:400px;width:90%;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,0.2);}' +
            '.sg-box h3{margin:0 0 0.5rem;font-size:1.2rem;color:#1a3a2a;}' +
            '.sg-box p{margin:0 0 1.25rem;color:#666;font-size:0.95rem;}' +
            '.sg-btn{padding:0.6rem 1.5rem;border:none;border-radius:8px;font-size:0.95rem;font-weight:600;cursor:pointer;font-family:inherit;}' +
            '.sg-btn-stay{background:#52b788;color:#fff;margin-right:0.5rem;}' +
            '.sg-btn-stay:hover{background:#40916c;}' +
            '.sg-btn-logout{background:transparent;border:1.5px solid #ccc;color:#666;}' +
            '.sg-btn-logout:hover{border-color:#ef4444;color:#ef4444;}';
        document.head.appendChild(style);

        modal = document.createElement('div');
        modal.className = 'sg-overlay';
        modal.innerHTML =
            '<div class="sg-box">' +
                '<h3>Oled veel kohal?</h3>' +
                '<p>Sind logitakse turvalisuse huvides 2 minuti p\u00e4rast automaatselt v\u00e4lja.</p>' +
                '<button class="sg-btn sg-btn-stay" id="sg-stay">J\u00e4\u00e4 sisse</button>' +
                '<button class="sg-btn sg-btn-logout" id="sg-logout-btn">Logi v\u00e4lja</button>' +
            '</div>';
        document.body.appendChild(modal);

        document.getElementById('sg-stay').addEventListener('click', function() { resetTimers(); });
        document.getElementById('sg-logout-btn').addEventListener('click', function() { doLogout(); });
    }

    // Only activate if user is logged in
    if (!localStorage.getItem('accessToken')) return;

    ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'].forEach(function(evt) {
        document.addEventListener(evt, resetTimers, { passive: true });
    });

    resetTimers();
})();
