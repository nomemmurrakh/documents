(function () {
const STORAGE_KEY = "sidebar-collapsed";

function init() {
  const sidebar = document.getElementById("sidebar");
  const toggle = document.getElementById("sidebar-toggle");
  const isDesktop = () => window.matchMedia("(min-width: 768px)").matches;

  if (!sidebar || !toggle) return;

  let backdrop = document.querySelector(".sidebar-backdrop");
  if (!backdrop) {
    backdrop = document.createElement("div");
    backdrop.className = "sidebar-backdrop";
    document.body.appendChild(backdrop);
  }

  const stored = localStorage.getItem(STORAGE_KEY);
  const collapsedByDefault = !isDesktop();
  const collapsed = stored === null ? collapsedByDefault : stored === "true";

  setState(!collapsed);

  toggle.addEventListener("click", () => {
    const isOpen = sidebar.classList.contains("is-open");
    setState(!isOpen);
    localStorage.setItem(STORAGE_KEY, String(isOpen));
  });

  backdrop.addEventListener("click", () => {
    setState(false);
    localStorage.setItem(STORAGE_KEY, "true");
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && sidebar.classList.contains("is-open") && !isDesktop()) {
      setState(false);
      localStorage.setItem(STORAGE_KEY, "true");
      toggle.focus();
    }
  });

  window.addEventListener("resize", () => {
    backdrop.classList.toggle("is-visible", sidebar.classList.contains("is-open") && !isDesktop());
  });

  function setState(open) {
    sidebar.classList.toggle("is-open", open);
    toggle.setAttribute("aria-expanded", String(open));
    backdrop.classList.toggle("is-visible", open && !isDesktop());
  }
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
})();
