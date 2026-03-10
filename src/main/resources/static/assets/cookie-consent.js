(function() {
    if (localStorage.getItem('cookieConsent')) return;

    var banner = document.createElement('div');
    banner.id = 'cookie-banner';
    banner.setAttribute('role', 'dialog');
    banner.setAttribute('aria-label', 'Küpsiste teavitus');
    banner.style.cssText = 'position:fixed;bottom:0;left:0;right:0;z-index:9998;background:#1B4332;color:white;padding:1rem 1.5rem;display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap;font-size:0.9rem;box-shadow:0 -4px 16px rgba(0,0,0,0.15);';

    banner.innerHTML = '<p style="margin:0;flex:1;min-width:200px;">Kasutame ainult hädavajalikke küpsiseid teenuse toimimiseks. Analüütika- ega reklaamiküpsiseid me ei kasuta. <a href="/privaatsuspoliitika.html" style="color:#95D5B2;text-decoration:underline;">Loe lähemalt</a></p>' +
        '<button id="cookie-accept" style="background:#52B788;color:white;border:none;padding:0.5rem 1.25rem;border-radius:8px;font-weight:600;cursor:pointer;font-size:0.9rem;white-space:nowrap;">Selge</button>';

    document.body.appendChild(banner);

    document.getElementById('cookie-accept').addEventListener('click', function() {
        localStorage.setItem('cookieConsent', 'accepted');
        banner.remove();
    });
})();
