(function () {
let index = [];
let cursor = -1;
let currentResults = [];

async function loadIndex() {
  try {
    const response = await fetch("/data/search-index.json");
    if (!response.ok) return;
    index = await response.json();
  } catch {
    index = [];
  }
}

function tokenize(text) {
  return text.toLowerCase().split(/\s+/).filter(Boolean);
}

function score(record, queryTokens) {
  const title = record.title.toLowerCase();
  const text = record.text.toLowerCase();
  let total = 0;

  for (const token of queryTokens) {
    const titleHits = countOccurrences(title, token);
    const textHits = countOccurrences(text, token);
    total += titleHits * 5 + textHits;
  }

  return total;
}

function countOccurrences(haystack, needle) {
  if (!needle) return 0;
  let count = 0;
  let pos = 0;
  while ((pos = haystack.indexOf(needle, pos)) !== -1) {
    count++;
    pos += needle.length;
  }
  return count;
}

function search(query) {
  const queryTokens = tokenize(query);
  if (queryTokens.length === 0) return [];

  return index
    .map((record) => ({ record, s: score(record, queryTokens) }))
    .filter(({ s }) => s > 0)
    .sort((a, b) => b.s - a.s)
    .slice(0, 8)
    .map(({ record }) => record);
}

function snippet(text, maxLen = 100) {
  if (text.length <= maxLen) return text;
  return text.slice(0, maxLen).trim() + "…";
}

function renderResults(results, resultsEl) {
  resultsEl.innerHTML = "";
  currentResults = results;
  cursor = -1;

  if (results.length === 0) {
    resultsEl.hidden = true;
    return;
  }

  for (const record of results) {
    const option = document.createElement("a");
    option.className = "search-result";
    option.setAttribute("role", "option");
    option.href = record.href;

    const title = document.createElement("div");
    title.className = "search-result__title";
    title.textContent = `${record.title}${record.section && record.section !== record.title ? " — " + record.section : ""}`;

    const excerpt = document.createElement("div");
    excerpt.className = "search-result__excerpt";
    excerpt.textContent = snippet(record.text);

    option.appendChild(title);
    option.appendChild(excerpt);
    resultsEl.appendChild(option);
  }

  resultsEl.hidden = false;
}

function updateCursor(resultsEl, delta) {
  const options = resultsEl.querySelectorAll(".search-result");
  if (options.length === 0) return;

  cursor = (cursor + delta + options.length) % options.length;

  options.forEach((opt, i) => {
    opt.classList.toggle("is-cursor", i === cursor);
  });
  options[cursor].scrollIntoView({ block: "nearest" });
}

function init() {
  const input = document.getElementById("search-input");
  const resultsEl = document.getElementById("search-results");
  if (!input || !resultsEl) return;

  loadIndex();

  input.addEventListener("input", () => {
    const results = search(input.value);
    renderResults(results, resultsEl);
  });

  input.addEventListener("keydown", (event) => {
    if (resultsEl.hidden) return;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      updateCursor(resultsEl, 1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      updateCursor(resultsEl, -1);
    } else if (event.key === "Enter") {
      if (cursor >= 0 && currentResults[cursor]) {
        event.preventDefault();
        window.location.href = currentResults[cursor].href;
      }
    } else if (event.key === "Escape") {
      resultsEl.hidden = true;
      cursor = -1;
    }
  });

  document.addEventListener("click", (event) => {
    if (!resultsEl.contains(event.target) && event.target !== input) {
      resultsEl.hidden = true;
    }
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
})();
