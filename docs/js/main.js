// DataDroid site: theme toggle, screenshot placeholders, scroll reveal, footer year.

(function () {
  const root = document.documentElement;

  // ---------- Theme toggle ----------

  const toggle = document.querySelector(".theme-toggle");
  const systemDark = window.matchMedia("(prefers-color-scheme: dark)");

  function currentTheme() {
    return root.dataset.theme || (systemDark.matches ? "dark" : "light");
  }

  function updateToggle() {
    if (!toggle) return;
    const next = currentTheme() === "dark" ? "light" : "dark";
    toggle.setAttribute("aria-label", "Switch to " + next + " theme");
  }

  if (toggle) {
    toggle.addEventListener("click", function () {
      const next = currentTheme() === "dark" ? "light" : "dark";
      root.dataset.theme = next;
      try {
        localStorage.setItem("theme", next);
      } catch (e) {
        /* Storage blocked: the choice lasts for this page only. */
      }
      updateToggle();
    });
    updateToggle();
    systemDark.addEventListener("change", updateToggle);
  }

  // ---------- Screenshots ----------
  // Each .screen shows a placeholder until its screenshot loads. Missing files keep the placeholder.

  document.querySelectorAll(".screen > img").forEach(function (img) {
    const screen = img.parentElement;
    const show = function () {
      screen.classList.add("has-image");
    };
    const hide = function () {
      img.remove();
    };
    if (img.complete) {
      img.naturalWidth > 0 ? show() : hide();
    } else {
      img.addEventListener("load", show, { once: true });
      img.addEventListener("error", hide, { once: true });
    }
  });

  // ---------- Reveal on scroll ----------

  const reveals = document.querySelectorAll(".reveal");
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (!("IntersectionObserver" in window) || reduceMotion) {
    reveals.forEach(function (el) {
      el.classList.add("is-visible");
    });
  } else {
    const observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { rootMargin: "0px 0px -8% 0px" }
    );
    reveals.forEach(function (el) {
      observer.observe(el);
    });
  }

  // ---------- Footer year ----------

  document.querySelectorAll("[data-year]").forEach(function (el) {
    el.textContent = String(new Date().getFullYear());
  });
})();
