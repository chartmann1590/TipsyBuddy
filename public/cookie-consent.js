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

  window.TipsyCookieConsent = {
    getConsent: readConsent,
    allowsOptional: function () {
      var consent = readConsent();
      return !!consent && consent.optional === true;
    },
    reset: function () {
      clearConsent();
      setBannerVisible(document.getElementById("cookieBanner"), true);
    }
  };
})();