#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const SKIP_DIRS = new Set(["partials", "assets", "data", "scripts", "src"]);

function findHtmlFiles(dir, base = "") {
  const out = [];
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    if (name.name.startsWith(".")) continue;
    const rel = path.join(base, name.name);
    const abs = path.join(dir, name.name);
    if (name.isDirectory()) {
      if (base === "" && SKIP_DIRS.has(name.name)) continue;
      out.push(...findHtmlFiles(abs, rel));
    } else if (name.name.endsWith(".html")) {
      out.push(rel);
    }
  }
  return out;
}

function main() {
  const files = findHtmlFiles(ROOT);
  let errors = 0;

  for (const relPath of files) {
    const html = fs.readFileSync(path.join(ROOT, relPath), "utf8");
    const refs = [...html.matchAll(/(?:href|src)="([^"]+)"/g)].map((m) => m[1]);

    for (const ref of refs) {
      if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("#") ||
          ref.startsWith("mailto:") || ref === "/api/") {
        continue;
      }
      const cleanRef = ref.split("#")[0].split("?")[0];
      const target = cleanRef.startsWith("/")
        ? path.join(ROOT, cleanRef)
        : path.join(ROOT, path.dirname(relPath), cleanRef);

      if (!fs.existsSync(target)) {
        console.error(`Broken link in ${relPath}: ${ref}`);
        errors++;
      }
    }
  }

  if (errors > 0) {
    console.error(`\n${errors} broken link(s) found.`);
    process.exit(1);
  }
  console.log(`Checked ${files.length} pages, all links resolve.`);
}

main();
