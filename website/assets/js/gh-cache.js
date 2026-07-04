export async function cachedFetch(key, url, ttlMs) {
  const cached = readCache(key);

  if (cached && Date.now() - cached.timestamp < ttlMs) {
    return cached.data;
  }

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Request failed: ${response.status}`);
    }
    const data = await response.json();
    writeCache(key, data);
    return data;
  } catch (error) {
    return cached ? cached.data : null;
  }
}

function readCache(key) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (typeof parsed.timestamp !== "number" || parsed.data === undefined) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function writeCache(key, data) {
  try {
    localStorage.setItem(key, JSON.stringify({ timestamp: Date.now(), data }));
  } catch {
    // Storage unavailable (private browsing, quota) — cache is best-effort only.
  }
}
