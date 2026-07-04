(function () {
function slugify(text) {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-");
}

function init() {
  const toc = document.getElementById("toc");
  const tocList = document.getElementById("toc-list");
  const main = document.getElementById("main-content");
  if (!toc || !tocList || !main) return;

  const headings = Array.from(main.querySelectorAll("h2, h3"));
  tocList.innerHTML = "";

  if (headings.length < 2) {
    toc.hidden = true;
    return;
  }

  toc.hidden = false;
  const usedSlugs = new Set();
  const links = [];

  for (const heading of headings) {
    if (!heading.id) {
      let slug = slugify(heading.textContent || "");
      let candidate = slug;
      let n = 1;
      while (usedSlugs.has(candidate)) {
        candidate = `${slug}-${n++}`;
      }
      heading.id = candidate;
      usedSlugs.add(candidate);
    } else {
      usedSlugs.add(heading.id);
    }

    const li = document.createElement("li");
    const a = document.createElement("a");
    a.href = `#${heading.id}`;
    a.textContent = heading.textContent;
    if (heading.tagName === "H3") {
      li.style.paddingLeft = "1em";
    }
    li.appendChild(a);
    tocList.appendChild(li);
    links.push({ heading, link: a });
  }

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const match = links.find((l) => l.heading === entry.target);
        if (!match) continue;
        if (entry.isIntersecting) {
          for (const l of links) l.link.classList.remove("is-active");
          match.link.classList.add("is-active");
        }
      }
    },
    { rootMargin: "0px 0px -70% 0px", threshold: 0 }
  );

  for (const { heading } of links) {
    observer.observe(heading);
  }
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
})();
