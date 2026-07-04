import { cachedFetch } from "./gh-cache.js";

const REPO = "nomemmurrakh/documents";
const ONE_HOUR_MS = 3600000;

async function loadStarCount() {
  const wrapper = document.getElementById("gh-star-count");
  const valueEl = document.getElementById("gh-star-count-value");
  if (!wrapper || !valueEl) return;

  const data = await cachedFetch(
    "gh-repo-stats",
    `https://api.github.com/repos/${REPO}`,
    ONE_HOUR_MS
  );

  if (!data || typeof data.stargazers_count !== "number") {
    wrapper.hidden = true;
    return;
  }

  valueEl.textContent = formatCount(data.stargazers_count);
}

async function loadContributors() {
  const container = document.getElementById("gh-contributors");
  const section = document.getElementById("contributors-section");
  if (!container) return;

  const data = await cachedFetch(
    "gh-contributors",
    `https://api.github.com/repos/${REPO}/contributors`,
    ONE_HOUR_MS
  );

  if (!Array.isArray(data) || data.length === 0) {
    container.hidden = true;
    if (section) section.hidden = true;
    return;
  }

  container.innerHTML = "";
  for (const contributor of data) {
    const link = document.createElement("a");
    link.href = contributor.html_url;
    link.target = "_blank";
    link.rel = "noopener";
    link.className = "contributor-avatar";
    link.title = contributor.login;

    const img = document.createElement("img");
    img.src = contributor.avatar_url;
    img.alt = contributor.login;
    img.width = 40;
    img.height = 40;
    img.loading = "lazy";

    link.appendChild(img);
    container.appendChild(link);
  }
}

function formatCount(n) {
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  }
  return String(n);
}

loadStarCount();
loadContributors();
