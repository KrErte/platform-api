/**
 * Pärandiplaan i18n — lightweight translation system.
 * Usage:
 *   1) Add data-i18n="key" to HTML elements
 *   2) Include <script src="/assets/i18n.js"></script> before </body>
 *   3) Call i18n.t('key') for dynamic strings
 */
(function() {
    'use strict';

    var translations = {};
    var currentLang = localStorage.getItem('lang') || 'et';

    var i18n = {
        lang: currentLang,

        /** Load translations for a language and apply to page */
        init: function() {
            var lang = this.lang;
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/assets/i18n/' + lang + '.json', true);
            xhr.onload = function() {
                if (xhr.status === 200) {
                    try {
                        translations = JSON.parse(xhr.responseText);
                    } catch(e) {
                        console.warn('i18n: failed to parse', lang, e);
                        translations = {};
                    }
                    i18n.apply();
                }
            };
            xhr.onerror = function() {
                console.warn('i18n: failed to load', lang);
            };
            xhr.send();
        },

        /** Apply translations to all data-i18n elements */
        apply: function() {
            var els = document.querySelectorAll('[data-i18n]');
            for (var i = 0; i < els.length; i++) {
                var key = els[i].getAttribute('data-i18n');
                var val = this.resolve(key);
                if (val !== undefined) {
                    // Support placeholder attribute
                    if (els[i].hasAttribute('data-i18n-attr')) {
                        var attr = els[i].getAttribute('data-i18n-attr');
                        els[i].setAttribute(attr, val);
                    } else {
                        els[i].textContent = val;
                    }
                }
            }
            // Update html lang
            document.documentElement.lang = this.lang;
        },

        /** Get a translation by dot-separated key */
        t: function(key, fallback) {
            var val = this.resolve(key);
            return val !== undefined ? val : (fallback || key);
        },

        /** Resolve a dot-notated key from translations */
        resolve: function(key) {
            var parts = key.split('.');
            var obj = translations;
            for (var i = 0; i < parts.length; i++) {
                if (obj == null) return undefined;
                obj = obj[parts[i]];
            }
            return obj;
        },

        /** Switch language and reload translations */
        setLang: function(lang) {
            this.lang = lang;
            localStorage.setItem('lang', lang);
            this.init();
        }
    };

    // Auto-initialize
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() { i18n.init(); });
    } else {
        i18n.init();
    }

    window.i18n = i18n;
})();
