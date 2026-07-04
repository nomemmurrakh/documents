#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { highlightCodeBlocks } = require("./highlight-kotlin.js");

const ROOT = path.resolve(__dirname, "..");
const PARTIALS_DIR = path.join(ROOT, "partials");
const SRC_DIR = path.join(ROOT, "src");
const NAV_PATH = path.join(ROOT, "data", "nav.json");

function readPartial(name) {
  return fs.readFileSync(path.join(PARTIALS_DIR, `${name}.html`), "utf8");
}

function renderNavTree(entries, currentHref) {
  const items = entries.map((entry) => {
    if (entry.children) {
      const children = renderNavTree(entry.children, currentHref);
      return `<li class="nav-group">
        <span class="nav-group__title">${entry.title}</span>
        <ul class="nav-group__list">${children}</ul>
      </li>`;
    }
    const external = entry.external ? ` target="_blank" rel="noopener"` : "";
    const current = entry.href === currentHref ? ` aria-current="page"` : "";
    return `<li><a href="${entry.href}"${external}${current}>${entry.title}</a></li>`;
  });
  return items.join("\n");
}

function findPageSources(dir, base = "") {
  const out = [];
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const rel = path.join(base, name.name);
    const abs = path.join(dir, name.name);
    if (name.isDirectory()) {
      out.push(...findPageSources(abs, rel));
    } else if (name.name.endsWith(".html")) {
      out.push(rel);
    }
  }
  return out;
}

function extractTitle(body, fallback) {
  const match = body.match(/<h1[^>]*>(.*?)<\/h1>/is);
  if (match) {
    return match[1].replace(/<[^>]+>/g, "").trim();
  }
  return fallback;
}

function buildPage(nav, relPath) {
  const srcPath = path.join(SRC_DIR, relPath);
  const body = highlightCodeBlocks(fs.readFileSync(srcPath, "utf8"));
  const href = "/" + relPath.replace(/\\/g, "/");

  const title = extractTitle(body, "Documents");
  const head = readPartial("head").replace(
    "<link rel=\"stylesheet\" href=\"/assets/css/main.css\">",
    `<title>${title} · Documents</title>\n<link rel="stylesheet" href="/assets/css/main.css">`
  );

  const header = readPartial("header");
  const sidebar = readPartial("sidebar").replace("<!--NAV_TREE-->", renderNavTree(nav, href));
  const footer = readPartial("footer");
  const toc = readPartial("toc");

  const html = `<!doctype html>
<html lang="en">
<head>
${head}
</head>
<body>
${header}
<div class="layout">
${sidebar}
<main id="main-content" class="main-content" tabindex="-1">
${body}
</main>
${toc}
</div>
${footer}
<script src="/assets/js/sidebar-toggle.js" defer></script>
<script src="/assets/js/scroll-restore.js" defer></script>
<script src="/assets/js/toc-scrollspy.js" defer></script>
<script type="module" src="/assets/js/github-stats.js"></script>
<script src="/assets/js/search.js" defer></script>
</body>
</html>
`;

  const outPath = path.join(ROOT, relPath);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, html, "utf8");
  return href;
}

function collectNavHrefs(entries, set) {
  for (const entry of entries) {
    if (entry.children) {
      collectNavHrefs(entry.children, set);
    } else if (!entry.external) {
      set.add(entry.href);
    }
  }
}

function main() {
  if (!fs.existsSync(NAV_PATH)) {
    console.error(`Missing nav file: ${NAV_PATH}`);
    process.exit(1);
  }
  const nav = JSON.parse(fs.readFileSync(NAV_PATH, "utf8"));

  for (const partial of ["head", "header", "sidebar", "footer", "toc"]) {
    const p = path.join(PARTIALS_DIR, `${partial}.html`);
    if (!fs.existsSync(p)) {
      console.error(`Missing partial: ${p}`);
      process.exit(1);
    }
  }

  if (!fs.existsSync(SRC_DIR)) {
    console.error(`Missing page source directory: ${SRC_DIR}`);
    process.exit(1);
  }

  const navHrefs = new Set();
  collectNavHrefs(nav, navHrefs);

  const sources = findPageSources(SRC_DIR);
  const built = new Set();

  for (const relPath of sources) {
    const href = buildPage(nav, relPath);
    built.add(href);
  }

  const missing = [...navHrefs].filter((href) => !built.has(href));
  if (missing.length > 0) {
    console.error("nav.json references pages with no source file:");
    for (const href of missing) console.error(`  ${href}`);
    process.exit(1);
  }

  console.log(`Built ${built.size} pages.`);
}

main();
