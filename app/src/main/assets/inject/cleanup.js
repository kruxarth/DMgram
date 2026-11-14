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

function hide(node) {
  if (!node || node.nodeType !== 1 || node === document.body || node === document.documentElement) return;
  if (node.tagName === "MAIN" || node.tagName === "HEADER") return;
  if (node.hasAttribute("data-dmgram-hidden")) return;
  node.setAttribute("data-dmgram-hidden", "");
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
  return tag === "SPAN" || tag === "A" || tag === "BUTTON" || tag === "H1" || tag === "H2" || tag === "H3" || tag === "svg" || el.getAttribute("role") === "button";
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

// Home header +: the Post/Story menu, drawn in the stories strip right under the native header. A WebView only
// opens the photo picker for a real tap on a file input, so each row has one of Instagram's own upload inputs
// (they live, hidden, in Instagram's header) stretched over it at opacity 0. Instagram's change handler then
// opens its composer exactly as its own + would.
let createMenu = null;

function createInputs() {
  const inputs = Array.from(document.querySelectorAll("header input[type='file']"));
  // Seen on device: the JPEG-only input opens /create/style/ (post), the AVIF/JPEG/PNG one /create/story/.
  const post = inputs.find((input) => (input.getAttribute("accept") || "").trim() === "image/jpeg") || null;
  const story = inputs.find((input) => input !== post) || null;
  return { post, story };
}

function createIcon(kind) {
  const paths = kind === "post"
    ? '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/>'
    : '<circle cx="12" cy="12" r="9" stroke-dasharray="3 2.2"/><path d="M12 8v8M8 12h8"/>';
  return '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
    'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths + "</svg>";
}

function openCreate(config) {
  if (CONFIG.surface !== "STORIES") return;
  closeCreate(false);
  const inputs = createInputs();
  if (!inputs.post && !inputs.story) {
    window.__dmgramLog("warn", "create: no upload inputs in the header");
    window.__dmgramPost({ type: "createClosed" });
    return;
  }
  // Instagram's own mode class on <html>; native's guess only until Instagram has picked one.
  const root = document.documentElement;
  const dark = root.classList.contains("__fb-dark-mode") ||
    (!root.classList.contains("__fb-light-mode") && !!(config && config.dark));
  const menu = document.createElement("div");
  menu.id = "dmgram-create";
  menu.setAttribute("role", "menu");
  menu.style.cssText = [
    "position:fixed", "top:4px", "right:" + Math.round((config && config.right) || 8) + "px", "z-index:2147483646",
    "min-width:148px", "padding:6px 0", "border-radius:12px",
    "background:" + (dark ? "#262626" : "#ffffff"), "color:" + (dark ? "#f5f5f5" : "#000000"),
    "box-shadow:0 4px 16px rgba(0,0,0," + (dark ? "0.5" : "0.15") + ")",
    "font:400 16px/20px -apple-system,system-ui,Roboto,sans-serif",
    "transform-origin:top right", "transition:opacity 120ms ease-out, transform 120ms ease-out",
    "opacity:0", "transform:scale(0.95)",
    // Taps fall through to the inputs: they sit in Instagram's fixed header, a stacking context of its own,
    // so no z-index can lift them above this menu.
    "pointer-events:none",
  ].join(";");
  const rows = [];
  for (const kind of ["post", "story"]) {
    const input = inputs[kind];
    if (!input) continue;
    const row = document.createElement("div");
    row.setAttribute("role", "menuitem");
    row.style.cssText = "display:flex;align-items:center;justify-content:space-between;gap:24px;height:44px;padding:0 16px";
    const label = document.createElement("span");
    label.textContent = String((config && config[kind]) || (kind === "post" ? "Post" : "Story"));
    row.appendChild(label);
    row.insertAdjacentHTML("beforeend", createIcon(kind));
    menu.appendChild(row);
    rows.push({ row, input, kind });
  }
  document.documentElement.appendChild(menu);
  for (const { row, input, kind } of rows) {
    // Un-hide the input's ancestors at zero height so the input itself can take taps. Stop where the tray's
    // branch joins: those ancestors are already shown, and collapsing them would collapse the tray too.
    for (let el = input.parentElement; el && el !== document.body && !el.hasAttribute("data-dmgram-tray-path"); el = el.parentElement) {
      el.setAttribute("data-dmgram-create-path", "");
    }
    input.setAttribute("data-dmgram-create-input", kind);
    // offsetTop/offsetWidth ignore the opening scale animation; the menu's resting box is what the finger sees.
    const menuBox = { left: window.innerWidth - Math.round((config && config.right) || 8) - menu.offsetWidth, top: 4 };
    const box = { left: menuBox.left + row.offsetLeft, top: menuBox.top + row.offsetTop, width: row.offsetWidth, height: row.offsetHeight };
    input.style.setProperty("left", box.left + "px", "important");
    input.style.setProperty("top", box.top + "px", "important");
    input.style.setProperty("width", box.width + "px", "important");
    input.style.setProperty("height", box.height + "px", "important");
    input.addEventListener("click", onCreatePick);
  }
  createMenu = menu;
  document.addEventListener("pointerdown", onCreateOutside, true);
  requestAnimationFrame(() => {
    menu.style.opacity = "1";
    menu.style.transform = "none";
  });
}

function onCreatePick(event) {
  if (event.currentTarget.getAttribute("data-dmgram-create-input") === "post") startPostSession();
  // The picker is opening; the input keeps its change handler wherever it sits, so the menu can go.
  setTimeout(() => closeCreate(true), 0);
}

// Instagram's own Post menu item opens the picker and starts a creation session in its Redux store; the
// post input's change handler throws without one (nullthrows on creation.sessionId). Stories need none.
function startPostSession() {
  try {
    const input = document.querySelector('[data-dmgram-create-input="post"]');
    const key = input && Object.keys(input).find((name) => name.startsWith("__reactFiber"));
    let store = null;
    for (let fiber = key ? input[key] : null; fiber && !store; fiber = fiber.return) {
      const props = fiber.memoizedProps;
      if (props && props.store && typeof props.store.dispatch === "function") store = props.store;
    }
    if (!store || typeof window.require !== "function") throw new Error("no store or require");
    const session = window.require("PolarisCreationActionStartCreationSession");
    const mode = window.require("PolarisCreationMode");
    store.dispatch(session.startCreationSession(mode.CreationMode.POST));
  } catch (error) {
    window.__dmgramLog("warn", "create: post session not started: " + error);
  }
}

function onCreateOutside(event) {
  const target = event.target;
  if (target && target.hasAttribute && target.hasAttribute("data-dmgram-create-input")) return;
  if (createMenu && createMenu.contains(target)) return;
  closeCreate(true);
  // Like any popup menu, the dismissing tap does nothing else (it would open the story under the finger).
  const swallow = (click) => {
    click.preventDefault();
    click.stopPropagation();
  };
  document.addEventListener("click", swallow, { capture: true, once: true });
  setTimeout(() => document.removeEventListener("click", swallow, true), 600);
}

function closeCreate(notify) {
  document.removeEventListener("pointerdown", onCreateOutside, true);
  document.querySelectorAll("[data-dmgram-create-input]").forEach((input) => {
    input.removeEventListener("click", onCreatePick);
    input.removeAttribute("data-dmgram-create-input");
    for (const prop of ["left", "top", "width", "height"]) input.style.removeProperty(prop);
  });
  document.querySelectorAll("[data-dmgram-create-path]").forEach((el) => el.removeAttribute("data-dmgram-create-path"));
  const had = !!createMenu;
  if (createMenu) createMenu.remove();
  createMenu = null;
  if (had && notify === true) window.__dmgramPost({ type: "createClosed" });
}

if (window.__dmgram) {
  window.__dmgram.openCreate = openCreate;
  window.__dmgram.closeCreate = closeCreate;
}

const SCAN_BUDGET_MS = 1.5;
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
  // Queued nodes Instagram has since removed (it recycles feed rows) need no scan.
  if (!node || node.nodeType !== 1 || !node.isConnected || node.hasAttribute("data-dmgram-hidden")) return;
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
  // Bounded by time only: a fixed node count let the queue fall behind a long scroll.
  while (pendingAt < pending.length && performance.now() - started < SCAN_BUDGET_MS) {
    scanNode(takePending());
  }
  if (pendingAt >= pending.length) {
    pending = [];
    pendingAt = 0;
  } else if (pendingAt > 1024) {
    // Never fully drained during a long scroll: drop the consumed head so the array doesn't grow without end.
    pending = pending.slice(pendingAt);
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

let placeScheduled = false;
let placedAt = "";

// Scroll events fire many times a frame (and on every keystroke that scrolls the thread): measure at most
// once per frame, and only touch the input's style when its position actually changed.
function schedulePlaceFileInput() {
  if (placeScheduled || !location.pathname.includes("/direct/")) return;
  placeScheduled = true;
  requestAnimationFrame(() => {
    placeScheduled = false;
    placeFileInput();
  });
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
  const key = [bounds.left - origin.left, bounds.top - origin.top, bounds.width, bounds.height].map(Math.round).join(",");
  if (key === placedAt && input.style.getPropertyValue("position") === "absolute") return;
  placedAt = key;
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
document.addEventListener("scroll", schedulePlaceFileInput, { capture: true, passive: true });
window.addEventListener("resize", schedulePlaceFileInput);
