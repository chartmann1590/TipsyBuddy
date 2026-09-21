// TipsyBuddy cookie consent banner.
// No first-party tracking cookies: the choice itself is stored in localStorage
// (not a cookie) under STORAGE_KEY so the banner stays dismissed.
(function () {
  "use strict";

  var STORAGE_KEY = "tipsybuddy_cookie_consent_v1";

  function readConsent() {
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed.optional !== "boolean") return null;
      return parsed;
    } catch (err) {
      return null;
    }
  }

  function writeConsent(optional) {
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          necessary: true,
          optional: !!optional,
          ts: new Date().toISOString()
        })
      );
      return true;
    } catch (err) {
      return false;
    }
  }

  function clearConsent() {
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch (err) {
      /* storage unavailable: banner will simply show again */
    }
  }

  function setBannerVisible(banner, visible) {
    if (!banner) return;
    if (visible) {
      banner.removeAttribute("hidden");
    } else {
      banner.setAttribute("hidden", "");
    }
  }

  function notifyLeafletReady() {
    try {
      var evt = new CustomEvent("tipsybuddy-leaflet-ready");
      if (typeof document !== "undefined" && document.dispatchEvent) {
        document.dispatchEvent(evt);
      }
      if (typeof window !== "undefined" && window.dispatchEvent) {
        window.dispatchEvent(evt);
      }
    } catch (err) {
      /* older browsers: app.js will still work once Leaflet is cached */
    }
  }

  function loadOptionalResources() {
    if (document.getElementById("tipsy-leaflet-js")) {
      notifyLeafletReady();
      return;
    }
    if (document.getElementById("tipsy-leaflet-css")) return;
    var css = document.createElement("link");
    css.rel = "stylesheet";
    css.id = "tipsy-leaflet-css";
    css.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
    css.integrity = "sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=";
    css.crossOrigin = "anonymous";
    document.head.appendChild(css);

    var preconnect1 = document.createElement("link");
    preconnect1.rel = "preconnect";
    preconnect1.id = "tipsy-preconnect-fonts";
    preconnect1.href = "https://fonts.googleapis.com";
    document.head.appendChild(preconnect1);

    var preconnect2 = document.createElement("link");
    preconnect2.rel = "preconnect";
    preconnect2.id = "tipsy-preconnect-gstatic";
    preconnect2.href = "https://fonts.gstatic.com";
    preconnect2.crossOrigin = "anonymous";
    document.head.appendChild(preconnect2);

    var fonts = document.createElement("link");
    fonts.rel = "stylesheet";
    fonts.id = "tipsy-fonts-css";
    fonts.href = "https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap";
    document.head.appendChild(fonts);

    var leafletScript = document.createElement("script");
    leafletScript.id = "tipsy-leaflet-js";
    leafletScript.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    leafletScript.integrity = "sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=";
    leafletScript.crossOrigin = "anonymous";
    leafletScript.onload = notifyLeafletReady;
    document.head.appendChild(leafletScript);
  }

  function allowsOptional() {
    var consent = readConsent();
    return !!consent && consent.optional === true;
  }

  function choose(optional) {
    var saved = writeConsent(optional);
    var banner = document.getElementById("cookieBanner");
    if (saved) {
      setBannerVisible(banner, false);
      updateSettingsStatus();
      try {
        window.dispatchEvent(
          new CustomEvent("tipsybuddy-cookie-consent", {
            detail: { optional: !!optional }
          })
        );
      } catch (err) {
        /* older browsers: choice is still stored */
      }
    }
  }

  function updateSettingsStatus() {
    var els = document.querySelectorAll("[data-cookie-status]");
    if (!els.length) return;
    var consent = readConsent();
    var text =
      !consent
        ? "You haven't saved a cookie choice yet."
        : consent.optional
          ? "Current choice: accepted non-essential cookies."
          : "Current choice: rejected non-essential cookies.";
    for (var i = 0; i < els.length; i++) {
      els[i].textContent = text;
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    var banner = document.getElementById("cookieBanner");
    if (banner && !readConsent()) {
      setBannerVisible(banner, true);
    } else if (allowsOptional()) {
      loadOptionalResources();
    }

    var acceptNodes = document.querySelectorAll("[data-cookie-accept]");
    for (var i = 0; i < acceptNodes.length; i++) {
      acceptNodes[i].addEventListener("click", function () {
        choose(true);
      });
    }

    var rejectNodes = document.querySelectorAll("[data-cookie-reject]");
    for (var j = 0; j < rejectNodes.length; j++) {
      rejectNodes[j].addEventListener("click", function () {
        choose(false);
      });
    }

    var resetNodes = document.querySelectorAll("[data-cookie-reset]");
    for (var k = 0; k < resetNodes.length; k++) {
      resetNodes[k].addEventListener("click", function () {
        clearConsent();
        updateSettingsStatus();
        setBannerVisible(banner, true);
        if (banner) {
          banner.scrollIntoView({ block: "nearest" });
        }
      });
    }

    updateSettingsStatus();
  });

  window.addEventListener("tipsybuddy-cookie-consent", function (e) {
    if (e.detail && e.detail.optional) {
      loadOptionalResources();
    }
  });

  window.TipsyCookieConsent = {
    getConsent: readConsent,
    allowsOptional: function () {
      var consent = readConsent();
      return !!consent && consent.optional === true;
    },
    reset: function () {
      clearConsent();
      updateSettingsStatus();
      var banner = document.getElementById("cookieBanner");
      setBannerVisible(banner, true);
      if (banner) {
        banner.scrollIntoView({ block: "nearest" });
      }
    }
  };
})();