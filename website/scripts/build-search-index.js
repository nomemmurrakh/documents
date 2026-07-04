#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const EXCLUDE = new Set(["style-guide.html"]);

function findHtmlFiles(dir, base = "") {
  const out = [];
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    if (name.name === "partials" || name.name === "assets" || name.name === "data" ||
        name.name === "scripts" || name.name === "src" || name.name === "api" ||
        name.name.startsWith(".")) {
      continue;
    }
    const rel = path.join(base, name.name);
    const abs = path.join(dir, name.name);
    if (name.isDirectory()) {
      out.push(...findHtmlFiles(abs, rel));
    } else if (name.name.endsWith(".html") && !EXCLUDE.has(rel)) {
      out.push(rel);
    }
  }
  return out;
}

const HTML_ENTITIES = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": "\"",
  "&#39;": "'",
  "&apos;": "'",
  "&nbsp;": " ",
};

function decodeEntities(text) {
  return text.replace(/&(?:amp|lt|gt|quot|#39|apos|nbsp);/g, (entity) => HTML_ENTITIES[entity]);
}

function stripTags(html) {
  return decodeEntities(
    html
      .replace(/<script[\s\S]*?<\/script>/gi, " ")
      .replace(/<style[\s\S]*?<\/style>/gi, " ")
      .replace(/<[^>]+>/g, " ")
      .replace(/\s+/g, " ")
      .trim()
  );
}

function extractSections(mainHtml) {
  const sections = [];
  const headingRe = /<h([12])[^>]*>(.*?)<\/h\1>/gis;
  const matches = [...mainHtml.matchAll(headingRe)];

  if (matches.length === 0) {
    return sections;
  }

  for (let i = 0; i < matches.length; i++) {
    const current = matches[i];
    const next = matches[i + 1];
    const start = current.index + current[0].length;
    const end = next ? next.index : mainHtml.length;
    const heading = stripTags(current[2]);
    const text = stripTags(mainHtml.slice(start, end));
    sections.push({ heading, text });
  }

  return sections;
}

function main() {
  const files = findHtmlFiles(ROOT);
  const records = [];

  for (const relPath of files) {
    const abs = path.join(ROOT, relPath);
    const html = fs.readFileSync(abs, "utf8");
    const href = "/" + relPath.replace(/\\/g, "/");

    const titleMatch = html.match(/<title>(.*?)<\/title>/is);
    const rawTitle = titleMatch ? stripTags(titleMatch[1]) : href;
    const title = rawTitle.replace(/\s*·\s*Documents$/, "");

    const mainMatch = html.match(/<main[^>]*>([\s\S]*?)<\/main>/i);
    const mainHtml = mainMatch ? mainMatch[1] : "";
    const sections = extractSections(mainHtml);

    if (sections.length === 0) {
      records.push({ title, href, section: title, text: stripTags(mainHtml) });
      continue;
    }

    for (const section of sections) {
      records.push({
        title,
        href,
        section: section.heading || title,
        text: section.text,
      });
    }
  }

  const outPath = path.join(ROOT, "data", "search-index.json");
  fs.writeFileSync(outPath, JSON.stringify(records, null, 2), "utf8");
  console.log(`Wrote ${records.length} search records to ${outPath}`);
}

main();
