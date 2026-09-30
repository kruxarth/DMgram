const listeners = {};

function dmgramEmit(event) {
  const list = listeners[event] || [];
  for (const fn of list) {
    try {
      fn(window.__dmgram);
    } catch (error) {
      dmgramLog("error", error && error.stack ? error.stack : error);
    }
  }
}

function dmgramLog(level, msg) {
  if (!CONFIG.debug) return;
  window.__dmgramPost({ type: "log", level: String(level).slice(0, 20), msg: String(msg).slice(0, 500) });
}

window.__dmgramEmit = dmgramEmit;
window.__dmgramLog = dmgramLog;

window.__dmgramPost = function (msg) {
  try {
    if (!window.DMGramNative || typeof DMGramNative.postMessage !== "function") return;
    DMGramNative.postMessage(JSON.stringify(msg));
  } catch (error) {
    console.error("DMGram postMessage failed", error);
  }
};

function pathOf(url) {
  try {
    return new URL(url, location.origin).pathname || "/";
  } catch (error) {
    return "/";
  }
}

function samePath(url) {
  const want = pathOf(url).replace(/\/$/, "") || "/";
  const have = (location.pathname || "/").replace(/\/$/, "") || "/";
  return want === have;
}

function findLink(path) {
  const want = path.startsWith("/") ? path : "/" + path;
  const bare = want.endsWith("/") ? want.slice(0, -1) : want;
  const slash = bare + "/";
  const links = document.querySelectorAll("a[href]");
  for (const link of links) {
    const href = link.getAttribute("href") || "";
    if (href === want || href === bare || href === slash) return link;
  }
  return null;
}

function navigate(path) {
  const target = path.startsWith("/") ? path : "/" + path;
  const absolute = new URL(target, location.origin).href;
  if (window.dmgramRouter && CONFIG.rules) {
    const decision = window.dmgramRouter.policy(CONFIG.tab, absolute, CONFIG.rules);
    if (decision.action === "BLOCK") {
      window.__dmgramPost({ type: "blocked", url: absolute, reason: decision.reason || "blocked" });
      window.__dmgram.lastNavigate = "block";
      return "block";
    }
    if (decision.action === "IGNORE") {
      window.__dmgram.lastNavigate = "ignore";
      return "ignore";
    }
    if (decision.action === "SWITCH" || decision.action === "EXTERNAL" || decision.action === "SYSTEM") {
      window.__dmgramPost({ type: "navigate", url: decision.url || absolute });
      window.__dmgram.lastNavigate = "native";
      dmgramLog("info", "navigate native " + target);
      return "native";
    }
  }
  const link = findLink(target);
  if (link) {
    link.click();
    if (samePath(target)) {
      window.__dmgram.lastNavigate = "click";
      dmgramLog("info", "navigate click " + target);
      return "click";
    }
  }
  const before = location.pathname + location.search;
  try {
    history.pushState({}, "", target);
    window.dispatchEvent(new PopStateEvent("popstate"));
  } catch (error) {
    dmgramLog("error", error && error.stack ? error.stack : error);
  }
  if ((location.pathname + location.search) !== before && samePath(target)) {
    window.__dmgram.lastNavigate = "pushState";
    dmgramLog("info", "navigate pushState " + target);
    return "pushState";
  }
  window.__dmgram.lastNavigate = "assign";
  dmgramLog("info", "navigate assign " + target);
  location.assign(target);
  return "assign";
}

function openSearch() {
  const step = navigate("/explore/");
  const focus = () => {
    const path = (location.pathname || "/").replace(/\/$/, "") || "/";
    if (path === "/explore") {
      history.pushState({}, "", "/explore/search/");
      window.dispatchEvent(new PopStateEvent("popstate"));
    }
    const input = document.querySelector('input[type="search"]');
    if (!input) return false;
    input.focus();
    return location.pathname.indexOf("/explore/search") === 0;
  };
  if (focus()) return step;
  const observer = new MutationObserver(() => {
    if (focus()) observer.disconnect();
  });
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
  setTimeout(() => observer.disconnect(), 2000);
  return step;
}

function scrollToTop() {
  try {
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (document.scrollingElement) {
      document.scrollingElement.scrollTo({ top: 0, behavior: "smooth" });
    }
  } catch (error) {
    dmgramLog("error", error && error.stack ? error.stack : error);
  }
}

function pauseMedia() {
  document.querySelectorAll("video, audio").forEach((media) => {
    try {
      media.pause();
    } catch (error) {
      dmgramLog("error", error && error.stack ? error.stack : error);
    }
  });
}

function setConfig(json) {
  const next = typeof json === "string" ? JSON.parse(json) : json;
  if (next.rules) CONFIG.rules = next.rules;
  if (next.tab) {
    CONFIG.tab = next.tab;
    window.__dmgram.tab = next.tab;
  }
  if (typeof next.debug === "boolean") CONFIG.debug = next.debug;
}

function setSystemDark(dark) {
  window.__dmgram.systemDark = !!dark;
  dmgramEmit("systemDark");
}

function back() {
  history.back();
}

function go(delta) {
  history.go(delta);
}

function on(event, fn) {
  (listeners[event] || (listeners[event] = [])).push(fn);
}

window.__dmgram = {
  tab: CONFIG.tab,
  route: "UNKNOWN",
  systemDark: false,
  lastNavigate: "",
  on,
  scrollToTop,
  pauseMedia,
  setConfig,
  setSystemDark,
  navigate,
  openSearch,
  back,
  go,
};

let atTop = true;
function publishScroll() {
  const top = (window.scrollY || 0) <= 2 && ((document.scrollingElement && document.scrollingElement.scrollTop) || 0) <= 2;
  if (top === atTop) return;
  atTop = top;
  window.__dmgramPost({ type: "scroll", atTop: top });
  dmgramEmit("scroll");
}
document.addEventListener("scroll", publishScroll, true);

let lastTheme = "";
function publishTheme() {
  const root = document.documentElement;
  if (!root) return;
  const dark = root.classList.contains("__fb-dark-mode");
  const background = getComputedStyle(document.body || root).backgroundColor || "";
  const key = (dark ? "1" : "0") + background;
  if (key === lastTheme) return;
  lastTheme = key;
  window.__dmgramPost({ type: "theme", dark, background: background.slice(0, 80) });
  dmgramEmit("theme");
}

let lastUsername = "";
function publishUsername() {
  const reserved = new Set(((CONFIG.rules && CONFIG.rules.reserved) || []).map((name) => String(name).toLowerCase()));
  const inbox = document.querySelector('a[href="/direct/inbox/"], a[href="/direct/inbox"]');
  if (!inbox) return;
  let bar = inbox.parentElement;
  for (let depth = 0; depth < 8 && bar; depth += 1) {
    const marks = bar.querySelectorAll('a[href="/"], a[href="/explore/"], a[href="/reels/"], a[href="/direct/inbox/"]');
    if (marks.length >= 4) break;
    bar = bar.parentElement;
  }
  if (!bar) return;
  for (const link of bar.querySelectorAll("a[href]")) {
    const match = (link.getAttribute("href") || "").match(/^\/([A-Za-z0-9._]{1,30})\/?$/);
    if (!match) continue;
    if (reserved.has(match[1].toLowerCase())) continue;
    if (match[1] === lastUsername) return;
    lastUsername = match[1];
    window.__dmgramPost({ type: "username", value: match[1] });
    return;
  }
}

let lastUnread = null;
function publishUnread() {
  if (CONFIG.tab !== "HOME") return;
  const link = document.querySelector('a[href="/direct/inbox/"], a[href="/direct/inbox"]');
  if (!link) return;
  let count = 0;
  for (const span of link.querySelectorAll("span")) {
    const text = (span.textContent || "").trim();
    if (/^\d+$/.test(text)) {
      count = parseInt(text, 10);
      break;
    }
  }
  if (count === lastUnread) return;
  lastUnread = count;
  window.__dmgramPost({ type: "unread", count });
}

let readyKey = "";
function publishReady() {
  const key = location.pathname + location.search;
  if (readyKey === key) return;
  const text = (document.body && document.body.innerText) || "";
  const ready = !!document.querySelector("article")
    || !!document.querySelector('[aria-label^="Story by "]')
    || /your story/i.test(text)
    || (location.pathname.indexOf("/direct/") === 0 && text.length > 80)
    || /log in/i.test(text);
  if (!ready) return;
  readyKey = key;
  window.__dmgramPost({ type: "ready" });
}

let scanScheduled = false;
function scheduleScan() {
  if (scanScheduled) return;
  scanScheduled = true;
  requestAnimationFrame(() => {
    scanScheduled = false;
    publishTheme();
    publishUsername();
    publishUnread();
    publishReady();
  });
}

function watchPage() {
  const root = document.documentElement;
  if (!root) return;
  new MutationObserver(scheduleScan).observe(root, { childList: true, subtree: true });
  new MutationObserver(publishTheme).observe(root, { attributes: true, attributeFilter: ["class"] });
  scheduleScan();
  publishScroll();
}

if (document.documentElement) watchPage();
else document.addEventListener("DOMContentLoaded", watchPage, { once: true });
