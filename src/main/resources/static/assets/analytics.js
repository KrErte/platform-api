/**
 * Plausible Analytics wrapper.
 * Include Plausible script in HTML pages for it to work:
 * <script defer data-domain="parandiplaan.ee" src="https://plausible.io/js/script.js"></script>
 */
window.analytics = {
    trackEvent: function(name, props) {
        if (typeof window.plausible === 'function') {
            window.plausible(name, { props: props || {} });
        }
    }
};
