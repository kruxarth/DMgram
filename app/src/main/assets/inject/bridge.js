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
    const decision = window.dmgramRouter.policy(CONFIG.tab, absolute, CONFIG.rules, CONFIG.surface);
    if (decision.action === "BLOCK") {
      window.__dmgramPost({ type: "blocked", url: absolute, reason: decision.reason || "blocked" });
      window.__dmgram.lastNavigate = "block";
      return "block";
    }
    if (decision.action === "IGNORE") {
      window.__dmgram.lastNavigate = "ignore";
      return "ignore";
    }
    if (decision.action === "SWITCH" || decision.action === "EXTERNAL" || decision.action === "SYSTEM" || decision.action === "FEED") {
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
      // Replace, not push: /explore/ must never be a back target.
      history.replaceState({}, "", "/explore/search/");
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
  if (typeof next.strip === "number") setStrip(next.strip);
  if (window.__dmgramApplyRules) window.__dmgramApplyRules();
}

// The Following feed sits under the native stories strip: hide.css pads the page by --dmgram-strip
// on the feed root so the first post starts right below the tray. Native keeps the value current.
function setStrip(px) {
  const value = Math.max(0, Math.min(400, Math.round(Number(px) || 0)));
  CONFIG.strip = value;
  const root = document.documentElement;
  if (!root) return;
  let style = document.getElementById("dmgram-strip");
  if (!style) {
    style = document.createElement("style");
    style.id = "dmgram-strip";
    root.appendChild(style);
  }
  const css = ":root{--dmgram-strip:" + value + "px}";
  if (style.textContent !== css) style.textContent = css;
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
  surface: CONFIG.surface || "PAGE",
  route: "UNKNOWN",
  systemDark: false,
  lastNavigate: "",
  on,
  scrollToTop,
  pauseMedia,
  setConfig,
  setSystemDark,
  setStrip,
  navigate,
  openSearch,
  back,
  go,
};

let atTop = true;
// Some pages scroll an inner box, not the document (the DM inbox list does). Remember the
// last vertically scrolling box, or native sees "at top" mid-list and turns every
// upward drag into pull-to-refresh.
let innerScroller = null;
function publishScroll(event) {
  const target = event && event.target;
  if (target && target.nodeType === 1 && target !== document.scrollingElement && target.scrollHeight > target.clientHeight + 1) {
    innerScroller = target;
  }
  if (innerScroller && !innerScroller.isConnected) innerScroller = null;
  const pageTop = (window.scrollY || 0) <= 2 && ((document.scrollingElement && document.scrollingElement.scrollTop) || 0) <= 2;
  const top = pageTop && (!innerScroller || innerScroller.scrollTop <= 2);
  if (top === atTop) return;
  atTop = top;
  window.__dmgramPost({ type: "scroll", atTop: top });
  dmgramEmit("scroll");
}
document.addEventListener("scroll", publishScroll, true);

// FEED only: native moves the stories strip with the feed, so report the offset at most once per frame.
// Past the cap the strip is fully off screen and nothing more needs to be sent.
const SCROLL_CAP = 1000;
let lastScrollY = -1;
let scrollYScheduled = false;
function publishScrollY() {
  if (CONFIG.surface !== "FEED" || scrollYScheduled) return;
  scrollYScheduled = true;
  requestAnimationFrame(() => {
    scrollYScheduled = false;
    const raw = window.scrollY || (document.scrollingElement && document.scrollingElement.scrollTop) || 0;
    const y = Math.max(0, Math.min(SCROLL_CAP, Math.round(raw)));
    if (y === lastScrollY) return;
    lastScrollY = y;
    window.__dmgramPost({ type: "scrollY", y });
  });
}
document.addEventListener("scroll", publishScrollY, { capture: true, passive: true });
on("route", () => {
  lastScrollY = -1;
  publishScrollY();
});

let lastTheme = "";
function publishTheme() {
  const root = document.documentElement;
  if (!root) return;
  // Until Instagram picks a mode, the page is unstyled white. Reporting that would flash light
  // native chrome over a dark page; native follows the system (as Instagram does) until then.
  if (!root.classList.contains("__fb-dark-mode") && !root.classList.contains("__fb-light-mode")) return;
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

// Your avatar for the native Profile tab. The image is already on the page (Instagram's hidden
// tab bar), so this reads the cached copy; it never asks for anything the page didn't load.
// Downscaled to 96 px so the message stays small. One attempt per image URL.
let avatarSrc = "";
function publishAvatar() {
  if (CONFIG.tab !== "HOME" || CONFIG.surface === "STORIES" || !lastUsername) return;
  const img = document.querySelector('a[href="/' + lastUsername + '/"] img');
  if (!img || !img.complete || !img.naturalWidth) return;
  const src = img.currentSrc || img.src;
  if (!src || src === avatarSrc) return;
  avatarSrc = src;
  fetch(src, { cache: "force-cache", credentials: "omit" })
    .then((res) => (res.ok ? res.blob() : Promise.reject(new Error("status " + res.status))))
    .then((blob) => createImageBitmap(blob))
    .then((bitmap) => {
      const canvas = document.createElement("canvas");
      canvas.width = 96;
      canvas.height = 96;
      canvas.getContext("2d").drawImage(bitmap, 0, 0, 96, 96);
      window.__dmgramPost({ type: "avatar", data: canvas.toDataURL("image/jpeg", 0.8) });
    })
    .catch((error) => dmgramLog("info", "avatar skipped " + error));
}

let lastUnread = null;
function publishUnread() {
  if (CONFIG.tab !== "HOME" || CONFIG.surface === "STORIES") return;
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
  const route = (window.__dmgram && window.__dmgram.route) || "";
  const key = route + location.pathname + location.search;
  if (readyKey === key) return;
  const text = (document.body && document.body.innerText) || "";
  let ready = false;
  if (route === "HOME_FEED") {
    ready = !!document.querySelector("article, [aria-label^='Story by '], [aria-label*='Your story' i]");
  } else if (route === "DIRECT_INBOX" || route === "DIRECT_THREAD") {
    ready = !!document.querySelector("[role='textbox'], textarea") || text.length > 40;
  } else if (route === "PROFILE") {
    ready = !!document.querySelector("header, article, img");
  } else if (route === "SEARCH") {
    ready = !!document.querySelector("input, [role='search']");
  } else if (route === "AUTH") {
    ready = /log in/i.test(text) || !!document.querySelector("input");
  } else if (route === "STORY" || route === "REEL_SINGLE" || route === "POST") {
    ready = !!document.querySelector("video, article, img");
  } else if (route) {
    ready = text.length > 40 || !!document.querySelector("article, input, video");
  }
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
    publishAvatar();
    publishUnread();
    publishReady();
  });
}

function watchPage() {
  const root = document.documentElement;
  if (!root) return;
  root.setAttribute("data-dmgram-surface", CONFIG.surface || "PAGE");
  setStrip(CONFIG.strip || 0);
  new MutationObserver(scheduleScan).observe(root, { childList: true, subtree: true });
  new MutationObserver(publishTheme).observe(root, { attributes: true, attributeFilter: ["class"] });
  scheduleScan();
  publishScroll();
}

if (document.documentElement) watchPage();
else document.addEventListener("DOMContentLoaded", watchPage, { once: true });
