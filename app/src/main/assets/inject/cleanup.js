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
  if (node.querySelector && node.querySelector("a[href='/reels/'], a[href='/reels']") && node.querySelector("article")) return;
  if (node.hasAttribute("data-dmgram-hidden")) return;
  node.setAttribute("data-dmgram-hidden", "");
}

function hide(node) {
  if (!node || node.nodeType !== 1) return;
  if (node.querySelectorAll && node.querySelectorAll("article").length > 1) return;
  conceal(node);
}

function shortText(el) {
  if (!el || el.nodeType !== 1 || el.childElementCount > 4) return "";
  const text = (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
  if (!text || text.length > 48) return "";
  return text;
}

function hideUnit(el) {
  const article = el.closest("article");
  if (article) {
    hide(article);
    return;
  }
  let node = el;
  let chosen = el;
  for (let depth = 0; depth < 8 && node.parentElement && node.parentElement !== document.body; depth += 1) {
    const parent = node.parentElement;
    if (parent.tagName === "MAIN" || parent.tagName === "HEADER") break;
    if (parent.querySelectorAll("article").length > 1) break;
    const rect = parent.getBoundingClientRect();
    if (rect.height > 520) break;
    if (holdsCaughtUp(parent)) break;
    chosen = parent;
    node = parent;
  }
  if (holdsCaughtUp(chosen)) {
    hide(el);
    return;
  }
  hide(chosen);
}

function holdsCaughtUp(node) {
  if (!node || !node.querySelectorAll) return false;
  const labels = markerList("caughtUp");
  for (const el of node.querySelectorAll("span, h1, h2, h3")) {
    if (labels.has(shortText(el))) return true;
  }
  return false;
}

function hideNag(el) {
  let node = el;
  for (let depth = 0; depth < 6 && node && node !== document.body; depth += 1) {
    const rect = node.getBoundingClientRect();
    const style = getComputedStyle(node);
    if (rect.height >= 24 && rect.height <= 120 && (style.position === "fixed" || style.position === "sticky")) {
      hide(node);
      return;
    }
    node = node.parentElement;
  }
  hide(el.closest("button, [role='button'], a") || el);
}

function cutAfter(marker) {
  document.documentElement.setAttribute("data-dmgram-caught", "");
  let node = marker;
  for (let depth = 0; depth < 10 && node && node.parentElement && node !== document.body; depth += 1) {
    let sibling = node.nextElementSibling;
    while (sibling) {
      conceal(sibling);
      sibling = sibling.nextElementSibling;
    }
    const parent = node.parentElement;
    if (parent.scrollHeight > window.innerHeight * 1.2) break;
    node = parent;
  }
}

function maybeHideUnfollowed(el) {
  const follow = markerList("follow");
  const control = el.closest("button, [role='button']");
  if (!control || !follow.has(shortText(control))) return;
  const article = control.closest("article");
  if (!article) return;
  const top = control.getBoundingClientRect().top - article.getBoundingClientRect().top;
  if (top < 0 || top > 180) return;
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
  if ((label && markerList("caughtUp").has(label)) || markerList("caughtUp").has(named)) {
    cutAfter(el);
    return;
  }
  if (label && (markerList("sponsored").has(label) || markerList("suggested").has(label))) {
    hideUnit(el);
    return;
  }
  if (label && markerList("follow").has(label)) maybeHideUnfollowed(el);
}

function markChrome() {
  const link = document.querySelector('a[href="/reels/"], a[href="/reels"]');
  if (!link) return;
  let el = link.parentElement;
  for (let depth = 0; depth < 8 && el && el !== document.body; depth += 1) {
    const rect = el.getBoundingClientRect();
    const inbox = el.querySelector('a[href="/direct/inbox/"], a[href="/direct/inbox"]');
    if (inbox && rect.height >= 48 && rect.height <= 140) {
      el.setAttribute("data-dmgram-chrome", "nav");
      return;
    }
    el = el.parentElement;
  }
}

function scanNode(node) {
  if (!node || node.nodeType !== 1 || node.hasAttribute("data-dmgram-checked")) return;
  const list = [node];
  if (node.querySelectorAll) {
    for (const el of node.querySelectorAll("span, a, button, h1, h2, h3, svg, [role='button']")) list.push(el);
  }
  if (document.documentElement.hasAttribute("data-dmgram-caught")) {
    if (node.tagName === "ARTICLE") hide(node);
    if (node.querySelectorAll) {
      for (const article of node.querySelectorAll("article")) hide(article);
    }
  }
  for (const el of list) {
    if (el.hasAttribute("data-dmgram-checked")) continue;
    el.setAttribute("data-dmgram-checked", "");
    classify(el);
  }
}

let pending = [];
let scheduled = false;
let samples = 0;
let sampleSum = 0;

function flush() {
  scheduled = false;
  const batch = pending;
  pending = [];
  const started = performance.now();
  for (const node of batch) scanNode(node);
  markChrome();
  const elapsed = performance.now() - started;
  sampleSum += elapsed;
  samples += 1;
  if (CONFIG.debug && window.__dmgramLog && (elapsed > 2 || samples % 40 === 0)) {
    window.__dmgramLog("info", "cleanup avgMs=" + (sampleSum / samples).toFixed(2) + " lastMs=" + elapsed.toFixed(2));
  }
}

function queue(node) {
  pending.push(node);
  if (scheduled) return;
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
}

window.__dmgramApplyRules = applyRemoteRules;
applyRemoteRules();

const cleanupObserver = new MutationObserver((records) => {
  const started = performance.now();
  for (const record of records) {
    for (const node of record.addedNodes) {
      if (node.nodeType === 1) pending.push(node);
    }
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
