"use strict";

const KEYWORDS = new Set([
  "val", "var", "fun", "class", "object", "interface", "data", "sealed", "enum",
  "internal", "private", "public", "protected", "override", "open", "abstract",
  "companion", "return", "if", "else", "when", "for", "while", "do", "in", "is",
  "as", "null", "true", "false", "this", "super", "import", "package", "typealias",
  "suspend", "inline", "reified", "vararg", "out", "by", "get", "set", "const",
  "init", "constructor", "annotation", "expect", "actual",
]);

const TOKEN_RE = new RegExp(
  [
    String.raw`(?<comment>//[^\n]*)`,
    String.raw`(?<string>"(?:[^"\\]|\\.)*")`,
    String.raw`(?<annotation>@[A-Za-z_][A-Za-z0-9_]*)`,
    String.raw`(?<number>\b\d[\d_]*(?:\.\d+)?[fFLl]?\b)`,
    String.raw`(?<ident>\b[A-Za-z_][A-Za-z0-9_]*\b)`,
  ].join("|"),
  "g"
);

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function highlightKotlin(source) {
  let out = "";
  let lastIndex = 0;
  let match;

  TOKEN_RE.lastIndex = 0;
  while ((match = TOKEN_RE.exec(source)) !== null) {
    out += escapeHtml(source.slice(lastIndex, match.index));
    const { comment, string, annotation, number, ident } = match.groups;

    if (comment) {
      out += `<span class="tok-comment">${escapeHtml(comment)}</span>`;
    } else if (string) {
      out += `<span class="tok-string">${escapeHtml(string)}</span>`;
    } else if (annotation) {
      out += `<span class="tok-annotation">${escapeHtml(annotation)}</span>`;
    } else if (number) {
      out += `<span class="tok-number">${escapeHtml(number)}</span>`;
    } else if (ident) {
      if (KEYWORDS.has(ident)) {
        out += `<span class="tok-keyword">${escapeHtml(ident)}</span>`;
      } else if (/^[A-Z]/.test(ident)) {
        out += `<span class="tok-type">${escapeHtml(ident)}</span>`;
      } else {
        out += escapeHtml(ident);
      }
    }

    lastIndex = TOKEN_RE.lastIndex;
  }
  out += escapeHtml(source.slice(lastIndex));
  return out;
}

function highlightCodeBlocks(html) {
  return html.replace(
    /<pre><code class="language-kotlin">([\s\S]*?)<\/code><\/pre>/g,
    (_, rawInner) => {
      const decoded = rawInner
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&");
      return `<pre><code class="language-kotlin">${highlightKotlin(decoded)}</code></pre>`;
    }
  );
}

module.exports = { highlightKotlin, highlightCodeBlocks };
