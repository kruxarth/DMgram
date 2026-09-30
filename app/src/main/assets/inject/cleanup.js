const markerSets = {};

function markerList(group) {
  if (markerSets[group]) return markerSets[group];
  const bag = (CONFIG.rules && CONFIG.rules.markers && CONFIG.rules.markers[group]) || {};
  const set = new Set();
  for (const list of Object.values(bag)) {
    if (!Array.isArray(list)) continue;
    for (const item of list) {
      const text = String(item).trim();
      if (text) set.add(text);
    }
  }
  markerSets[group] = set;
  return set;
}

function refreshMarkers() {
  for (const key of Object.keys(markerSets)) delete markerSets[key];
}

function conceal(node) {
  if (!node || node.nodeType !== 1 || node === document.body || node === document.documentElement) return;
  if (node.tagName === "MAIN" || node.tagName === "HEADER" || node.tagName === "HTML") return;
  if (node.hasAttribute("data-dmgram-hidden")) return;
  node.setAttribute("data-dmgram-hidden", "");
}

function hide(node) {
  if (!node || node.nodeType !== 1) return;
  conceal(node);
}

function shortText(el) {
  if (!el || el.nodeType !== 1 || el.childElementCount > 0) return "";
  const raw = el.firstChild && el.firstChild.nodeType === 3 ? el.firstChild.data : "";
  if (!raw || raw.length > 48) return "";
  const text = raw.replace(/\s+/g, " ").trim();
  if (!text || text.length > 48) return "";
  return text;
}

function hideUnit(el) {
  const article = el.closest("article");
  if (article) {
    hide(article);
    return;
  }
  hide(el.closest("button, [role='button'], a, h1, h2, h3") || el);
}

function hideNag(el) {
  hide(el.closest("button, [role='button'], a") || el);
}

function maybeHideUnfollowed(el) {
  const follow = markerList("follow");
  const control = el.closest("button, [role='button']");
  if (!control || !follow.has(shortText(control))) return;
  const header = control.closest("header");
  const article = header && header.closest("article");
  if (!article) return;
  hide(article);
}

function classify(el) {
  const label = shortText(el);
  const named = (el.getAttribute("aria-label") || "").trim();
  if (named === "Similar accounts" || markerList("similar").has(named)) {
    hide(el.closest("button, [role='button'], a") || el);
    return;
  }
  if ((label && markerList("nags").has(label)) || markerList("nags").has(named)) {
    hideNag(el);
    return;
  }
  if (label && (markerList("sponsored").has(label) || markerList("suggested").has(label))) {
    hideUnit(el);
    return;
  }
  if (label && markerList("follow").has(label)) maybeHideUnfollowed(el);
}

let chromeEl = null;

// Instagram re-renders its bottom nav per route, and the Search variant has no Reels link.
// Mark the smallest bar that holds both Home and Inbox, outside the page header, again whenever the old one is gone.
function markChrome() {
  if (chromeEl && chromeEl.isConnected) return;
  chromeEl = null;
  for (const link of document.querySelectorAll('a[href="/direct/inbox/"], a[href="/direct/inbox"]')) {
    if (link.closest("header")) continue;
    let el = link.parentElement;
    for (let depth = 0; depth < 8 && el && el !== document.body; depth += 1) {
      if (el.childElementCount > 24) break;
      if (el.querySelector('a[href="/"]')) {
        el.setAttribute("data-dmgram-chrome", "nav");
        chromeEl = el;
        return;
      }
      el = el.parentElement;
    }
  }
}

function isLabel(el) {
  const tag = el.tagName;
  return tag === "SPAN" || tag === "A" || tag === "BUTTON" || tag === "H1" || tag === "H2" || tag === "H3" || tag === "SVG" || el.getAttribute("role") === "button";
}

function classifyIfLabel(el) {
  if (!isLabel(el)) return;
  if (el.childElementCount > 0 && !(el.getAttribute("aria-label") || "").trim()) return;
  classify(el);
}

function insideHidden(node) {
  let el = node;
  for (let depth = 0; depth < 14 && el && el !== document.body; depth += 1) {
    if (el.hasAttribute && el.hasAttribute("data-dmgram-hidden")) return true;
    el = el.parentElement;
  }
  return false;
}

function scanArticle(article) {
  if (!article || article.hasAttribute("data-dmgram-checked") || insideHidden(article)) return;
  article.setAttribute("data-dmgram-checked", "");
  const stack = [article];
  let seen = 0;
  while (stack.length && seen < 80) {
    const el = stack.pop();
    if (!el || el.nodeType !== 1 || (el !== article && el.tagName === "ARTICLE")) continue;
    seen += 1;
    if (el !== article) classifyIfLabel(el);
    const kids = el.children;
    const limit = Math.min(kids.length, 8);
    for (let i = limit - 1; i >= 0; i -= 1) stack.push(kids[i]);
  }
}

// ---- Home stories strip (surface STORIES) ----
// The strip loads Instagram's normal home only for its stories tray. Mark the tray block and the path
// above it; hide.css then switches off everything else, so the feed never shows, paginates or plays.

let trayBlock = null;
let trayHeight = 0;
let traySizer = null;

function isHorizontalScroller(el) {
  if (!el || el.nodeType !== 1 || el.scrollWidth <= el.clientWidth + 8) return false;
  const overflow = getComputedStyle(el).overflowX;
  return overflow === "auto" || overflow === "scroll";
}

function findTrayScroller() {
  const main = document.querySelector("main");
  if (!main) return null;
  // English label first; then by shape: the first horizontal scroller holding several buttons.
  const starts = [];
  const labelled = main.querySelector('[aria-label^="Story by"]');
  if (labelled) starts.push(labelled);
  const buttons = main.querySelectorAll('[role="button"]');
  for (let i = 0; i < buttons.length && i < 30; i += 1) starts.push(buttons[i]);
  for (const start of starts) {
    let el = start.parentElement;
    for (let depth = 0; depth < 8 && el && el !== main; depth += 1) {
      if (isHorizontalScroller(el) && el.querySelectorAll('[role="button"]').length >= 2) return el;
      el = el.parentElement;
    }
  }
  return null;
}

function trayBlockFrom(scroller) {
  const limit = scroller.getBoundingClientRect().height + 40;
  let block = scroller;
  while (block.parentElement && block.parentElement !== document.body) {
    const parent = block.parentElement;
    if (parent.tagName === "MAIN" || parent.querySelector("article")) break;
    if (parent.getBoundingClientRect().height > limit) break;
    block = parent;
  }
  return block;
}

function clearTray() {
  document.querySelectorAll("[data-dmgram-tray], [data-dmgram-tray-path]").forEach((el) => {
    el.removeAttribute("data-dmgram-tray");
    el.removeAttribute("data-dmgram-tray-path");
  });
  if (traySizer) traySizer.disconnect();
  traySizer = null;
  trayBlock = null;
}

function publishTray() {
  if (!trayBlock || !trayBlock.isConnected) return;
  const bottom = Math.ceil(trayBlock.getBoundingClientRect().bottom + (window.scrollY || 0));
  if (bottom < 20 || bottom === trayHeight) return;
  trayHeight = bottom;
  window.__dmgramPost({ type: "tray", height: bottom });
}

function markTray() {
  if (CONFIG.surface !== "STORIES") return;
  if ((window.__dmgram && window.__dmgram.route) !== "HOME_FEED") return;
  // Re-mark when the block vanished, or when it grew to take in the feed (it was marked before posts loaded).
  if (trayBlock && trayBlock.isConnected && !trayBlock.querySelector("article")) return;
  clearTray();
  const scroller = findTrayScroller();
  if (!scroller) return;
  trayBlock = trayBlockFrom(scroller);
  trayBlock.setAttribute("data-dmgram-tray", "");
  for (let el = trayBlock.parentElement; el; el = el.parentElement) {
    el.setAttribute("data-dmgram-tray-path", "");
    if (el === document.body) break;
  }
  if (typeof ResizeObserver === "function") {
    traySizer = new ResizeObserver(publishTray);
    traySizer.observe(trayBlock);
  }
  publishTray();
}

if (CONFIG.surface === "STORIES" && window.__dmgram && window.__dmgram.on) {
  // Back from a story: the strip shows the top of the page again.
  window.__dmgram.on("route", (dmgram) => {
    if (dmgram.route !== "HOME_FEED") return;
    window.scrollTo(0, 0);
    trayHeight = 0;
    markTray();
  });
}

const kidCursor = new WeakMap();
let pending = [];
let pendingAt = 0;
let scheduled = false;
let samples = 0;
let sampleSum = 0;
let maxMs = 0;

function enqueue(node) {
  if (!node || node.nodeType !== 1) return;
  pending.push(node);
}

function takePending() {
  if (pendingAt >= pending.length) return null;
  const node = pending[pendingAt];
  pending[pendingAt] = null;
  pendingAt += 1;
  return node;
}

function scanNode(node) {
  if (!node || node.nodeType !== 1 || node.hasAttribute("data-dmgram-hidden")) return;
  if (node === document.body || node === document.documentElement) {
    for (const child of node.children) enqueue(child);
    return;
  }
  if (node.tagName === "ARTICLE") {
    // The stories strip never shows its feed (hide.css switches it off), so its posts aren't worth scanning.
    if (CONFIG.surface !== "STORIES") scanArticle(node);
    return;
  }
  const resume = kidCursor.get(node) || 0;
  if (!resume) classifyIfLabel(node);
  const kids = node.children;
  const end = Math.min(kids.length, resume + 12);
  for (let i = resume; i < end; i += 1) enqueue(kids[i]);
  if (end < kids.length) {
    kidCursor.set(node, end);
    enqueue(node);
  } else if (resume) kidCursor.delete(node);
}

function flush() {
  scheduled = false;
  markChrome();
  markTray();
  const started = performance.now();
  let processed = 0;
  while (pendingAt < pending.length && processed < 5 && performance.now() - started < 1) {
    scanNode(takePending());
    processed += 1;
  }
  if (pendingAt >= pending.length) {
    pending = [];
    pendingAt = 0;
  }
  if (pending.length) {
    scheduled = true;
    requestAnimationFrame(flush);
  }
  const elapsed = performance.now() - started;
  sampleSum += elapsed;
  samples += 1;
  if (elapsed > maxMs) maxMs = elapsed;
  const tab = (window.__dmgram && window.__dmgram.tab) || "?";
  if (CONFIG.debug && window.__dmgramLog && (samples <= 2 || elapsed >= maxMs || samples % 20 === 0)) {
    window.__dmgramLog("info", "cleanup tab=" + tab + " avgMs=" + (sampleSum / samples).toFixed(2) + " lastMs=" + elapsed.toFixed(2) + " maxMs=" + maxMs.toFixed(2));
  }
}

function queue(node) {
  enqueue(node);
  if (scheduled || !pending.length) return;
  scheduled = true;
  requestAnimationFrame(flush);
}

function applyRemoteRules() {
  refreshMarkers();
  const rules = CONFIG.rules || {};
  const parts = [];
  for (const rule of rules.hide || []) {
    if (!rule || typeof rule.selector !== "string" || !rule.selector) continue;
    parts.push(rule.selector + "{display:none!important}");
  }
  if (typeof rules.css === "string" && rules.css) parts.push(rules.css);
  let style = document.getElementById("dmgram-remote");
  if (!style) {
    style = document.createElement("style");
    style.id = "dmgram-remote";
    (document.head || document.documentElement).appendChild(style);
  }
  style.textContent = parts.join("\n");
  markerList("similar");
  markerList("nags");
  markerList("sponsored");
  markerList("suggested");
  markerList("follow");
}

function placeFileInput() {
  if (!location.pathname.includes("/direct/")) return;
  const input = document.querySelector("input[type='file']");
  const svg = document.querySelector('svg[aria-label="Add Photo or Video"]');
  if (!input || !svg) return;
  const button = svg.parentElement || svg;
  const bounds = button.getBoundingClientRect();
  if (bounds.width < 8 || bounds.bottom < 0 || bounds.top > window.innerHeight) return;
  const parent = input.offsetParent || document.body;
  const origin = parent.getBoundingClientRect();
  input.style.setProperty("display", "block", "important");
  input.style.setProperty("position", "absolute", "important");
  input.style.setProperty("opacity", "0", "important");
  input.style.setProperty("z-index", "2147483647", "important");
  input.style.setProperty("margin", "0", "important");
  input.style.setProperty("padding", "0", "important");
  input.style.setProperty("left", (bounds.left - origin.left) + "px", "important");
  input.style.setProperty("top", (bounds.top - origin.top) + "px", "important");
  input.style.setProperty("width", Math.max(bounds.width, 32) + "px", "important");
  input.style.setProperty("height", Math.max(bounds.height, 32) + "px", "important");
}

window.__dmgramApplyRules = applyRemoteRules;
applyRemoteRules();

function scheduleChrome() {
  setTimeout(() => {
    markChrome();
    scheduleChrome();
  }, 500);
}

const cleanupObserver = new MutationObserver((records) => {
  const started = performance.now();
  for (const record of records) {
    const added = record.addedNodes;
    if (added.length > 8 && record.target && record.target.nodeType === 1) {
      enqueue(record.target);
      continue;
    }
    for (const node of added) enqueue(node);
  }
  const elapsed = performance.now() - started;
  if (CONFIG.debug && window.__dmgramLog && elapsed > 2) {
    window.__dmgramLog("info", "cleanup observerMs=" + elapsed.toFixed(2));
  }
  if (!scheduled && pending.length) {
    scheduled = true;
    requestAnimationFrame(flush);
  }
});

if (document.documentElement) {
  cleanupObserver.observe(document.documentElement, { childList: true, subtree: true });
  if (document.body) queue(document.body);
}
scheduleChrome();
document.addEventListener("scroll", () => placeFileInput(), true);
window.addEventListener("resize", () => placeFileInput());
