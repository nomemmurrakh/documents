(function () {
const STORAGE_KEY = "sidebar-scroll-pos";

function init() {
  const sidebar = document.getElementById("sidebar");
  if (!sidebar) return;

  restore(sidebar);

  sidebar.addEventListener("scroll", () => {
    sessionStorage.setItem(STORAGE_KEY, String(sidebar.scrollTop));
  });

  sidebar.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      sessionStorage.setItem(STORAGE_KEY, String(sidebar.scrollTop));
    });
  });

  window.addEventListener("pagehide", () => {
    sessionStorage.setItem(STORAGE_KEY, String(sidebar.scrollTop));
  });
}

function restore(sidebar) {
  const saved = sessionStorage.getItem(STORAGE_KEY);
  if (saved === null) return;

  const y = Number(saved);
  if (!Number.isFinite(y)) return;

  sidebar.scrollTop = y;
  requestAnimationFrame(() => {
    sidebar.scrollTop = y;
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
})();
