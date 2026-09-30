(function (root) {
  const INSTAGRAM_HOSTS = {
    "instagram.com": true,
    "www.instagram.com": true,
    "m.instagram.com": true,
  };

  function normalizePath(path) {
    if (!path) return "/";
    return path;
  }

  function firstSegment(path) {
    const parts = String(path).split("/");
    for (const part of parts) {
      if (part) return part.toLowerCase();
    }
    return "";
  }

  function unwrap(parsed) {
    if (parsed.hostname.toLowerCase() !== "l.instagram.com") return parsed;
    const inner = parsed.searchParams.get("u");
    if (!inner) return parsed;
    try {
      return new URL(inner);
    } catch (error) {
      return parsed;
    }
  }

  function classify(raw, rules) {
    let parsed;
    try {
      parsed = new URL(String(raw).trim());
    } catch (error) {
      return { class: "EXTERNAL", url: String(raw), scheme: "invalid" };
    }
    const scheme = (parsed.protocol || "").replace(":", "").toLowerCase();
    if (scheme !== "http" && scheme !== "https") {
      return { class: "EXTERNAL", url: parsed.toString(), scheme: scheme || "unknown" };
    }
    const target = unwrap(parsed);
    // The shim's target is web-supplied: it gets the same scheme check as a direct link.
    const innerScheme = (target.protocol || "").replace(":", "").toLowerCase();
    if (innerScheme !== "http" && innerScheme !== "https") {
      return { class: "EXTERNAL", url: target.toString(), scheme: innerScheme || "unknown" };
    }
    const host = target.hostname.toLowerCase();
    if (host === "l.instagram.com") {
      return { class: "EXTERNAL", url: target.toString(), scheme: "https" };
    }
    const path = normalizePath(target.pathname);
    const reserved = new Set(((rules && rules.reserved) || []).map((name) => String(name).toLowerCase()));
    const routes = (rules && rules.routes) || [];
    for (const route of routes) {
      const hosts = route.hosts || [];
      const hostOk = hosts.length === 0 ? !!INSTAGRAM_HOSTS[host] : hosts.some((item) => item.toLowerCase() === host) && (host === "instagram.com" || host.endsWith(".instagram.com"));
      if (!hostOk) continue;
      if (!new RegExp(route.pattern).test(path)) continue;
      if (route.class === "PROFILE" && reserved.has(firstSegment(path))) continue;
      return { class: route.class, url: target.toString(), scheme: (target.protocol || "").replace(":", "").toLowerCase() };
    }
    if (INSTAGRAM_HOSTS[host] || host.endsWith(".instagram.com")) {
      return { class: "UNKNOWN", url: target.toString(), scheme: "https" };
    }
    return { class: "EXTERNAL", url: target.toString(), scheme: "https" };
  }

  // Pages the stories strip hands to the feed below it; mirrors STORIES_HANDOFF in NavPolicy.kt.
  const STORIES_HANDOFF = { PROFILE: true, POST: true, REEL_SINGLE: true, DIRECT_THREAD: true, SEARCH: true, ACTIVITY: true };

  function policy(tab, raw, rules, surface) {
    const decision = basePolicy(tab, raw, rules);
    if (surface === "STORIES" && decision.action === "ALLOW" && STORIES_HANDOFF[decision.class]) {
      return { action: "FEED", class: decision.class, url: decision.url, tab: null };
    }
    return decision;
  }

  function basePolicy(tab, raw, rules) {
    const classified = classify(raw, rules);
    const route = classified.class;
    if (route === "EXPLORE_BLOCKED") return { action: "BLOCK", reason: "explore", class: route, url: classified.url, tab: null };
    if (route === "REELS_FEED") return { action: "BLOCK", reason: "reels", class: route, url: classified.url, tab: null };
    if (route === "HOME_FEED") {
      if (tab === "HOME") return { action: "ALLOW", class: route, url: classified.url, tab: null };
      return { action: "SWITCH", class: route, url: classified.url, tab: "HOME" };
    }
    if (route === "DIRECT_INBOX") {
      if (tab === "DMS") return { action: "ALLOW", class: route, url: classified.url, tab: null };
      return { action: "SWITCH", class: route, url: classified.url, tab: "DMS" };
    }
    if (route === "EXTERNAL") {
      if (classified.scheme === "mailto" || classified.scheme === "tel") {
        return { action: "SYSTEM", class: route, url: classified.url, tab: null };
      }
      if (classified.scheme === "http" || classified.scheme === "https") {
        return { action: "EXTERNAL", class: route, url: classified.url, tab: null };
      }
      return { action: "IGNORE", class: route, url: classified.url, tab: null };
    }
    return { action: "ALLOW", class: route, url: classified.url, tab: null };
  }

  function install() {
    const dmgram = root.__dmgram;
    if (!dmgram || root.__dmgramHistory) return;
    root.__dmgramHistory = true;

    function trail(path) {
      if (!path || path === "/") return "/";
      return path.endsWith("/") ? path : path + "/";
    }

    function reportRoute() {
      let route = "UNKNOWN";
      try {
        route = classify(location.href, CONFIG.rules).class;
      } catch (error) {
        if (window.__dmgramLog) window.__dmgramLog("error", error && error.stack ? error.stack : error);
      }
      dmgram.route = route;
      const rootEl = document.documentElement;
      if (rootEl) {
        rootEl.setAttribute("data-dmgram-route", route);
        rootEl.setAttribute("data-dmgram-path", trail(location.pathname));
      }
      let index = -1;
      try {
        if (window.navigation && window.navigation.currentEntry) index = window.navigation.currentEntry.index;
      } catch (error) {
        index = -1;
      }
      window.__dmgramPost({ type: "route", url: location.href, index: index });
      if (window.__dmgramEmit) window.__dmgramEmit("route");
      syncReelObserver();
    }

    const pushState = history.pushState;
    const replaceState = history.replaceState;
    history.pushState = function () {
      const result = pushState.apply(this, arguments);
      reportRoute();
      return result;
    };
    history.replaceState = function () {
      const result = replaceState.apply(this, arguments);
      reportRoute();
      return result;
    };
    window.addEventListener("popstate", reportRoute);

    function onClick(event) {
      const node = event.target && event.target.nodeType === 1 ? event.target : event.target && event.target.parentElement;
      const link = node && node.closest ? node.closest("a[href]") : null;
      if (!link) return;
      const href = link.href || link.getAttribute("href") || "";
      let decision;
      try {
        decision = policy(CONFIG.tab, href, CONFIG.rules, CONFIG.surface);
      } catch (error) {
        window.__dmgramLog("error", error && error.stack ? error.stack : error);
        return;
      }
      if (decision.action === "ALLOW") return;
      event.preventDefault();
      event.stopImmediatePropagation();
      if (decision.action === "BLOCK") {
        window.__dmgramPost({ type: "blocked", url: href, reason: decision.reason || "blocked" });
        return;
      }
      if (decision.action === "IGNORE") return;
      window.__dmgramPost({ type: "navigate", url: decision.url || href });
    }
    document.addEventListener("click", onClick, true);
    document.addEventListener("auxclick", onClick, true);

    let reelObserver = null;
    let reelCheckScheduled = false;
    let reelScroller = null;
    let reelHost = null;
    let reelTouch = null;

    function blockGesture(event) {
      if (!document.documentElement || !document.documentElement.hasAttribute("data-dmgram-reel-lock")) return;
      event.preventDefault();
    }

    function painted(node) {
      let el = node;
      while (el && el.nodeType === 1) {
        const style = getComputedStyle(el);
        if (style.display === "none" || style.visibility === "hidden") return false;
        const opacity = parseFloat(style.opacity);
        if (!Number.isNaN(opacity) && opacity === 0) return false;
        el = el.parentElement;
      }
      return true;
    }

    function intersectsViewport(rect) {
      return rect.bottom > 0 && rect.top < window.innerHeight && rect.right > 0 && rect.left < window.innerWidth;
    }

    function unlockReel() {
      const html = document.documentElement;
      if (html) html.removeAttribute("data-dmgram-reel-lock");
      if (reelHost) reelHost.removeAttribute("data-dmgram-reel-host");
      reelHost = null;
      if (reelScroller) {
        reelScroller.removeAttribute("data-dmgram-reel-scroller");
        reelScroller.querySelectorAll("[data-dmgram-reel-extra]").forEach((node) => {
          node.removeAttribute("data-dmgram-reel-extra");
        });
      }
      if (reelTouch) {
        document.removeEventListener("touchmove", reelTouch, true);
        document.removeEventListener("wheel", reelTouch, true);
        reelTouch = null;
      }
      reelScroller = null;
      if (dmgram.reelLocked) {
        dmgram.reelLocked = false;
        window.__dmgramPost({ type: "reelLock", active: false });
      }
    }

    function lockReel(scroller) {
      const items = [];
      for (const video of scroller.querySelectorAll("video")) {
        let item = video;
        while (item.parentElement && item.parentElement !== scroller) item = item.parentElement;
        if (items.indexOf(item) === -1) items.push(item);
      }
      if (items.length < 2) return;
      let keep = items[0];
      let best = Infinity;
      for (const item of items) {
        const rect = item.getBoundingClientRect();
        const distance = Math.abs(rect.top);
        if (distance < best) {
          best = distance;
          keep = item;
        }
      }
      if (window.__dmgramLog) window.__dmgramLog("info", "reel lock items=" + items.length + " firstIsVisible=" + (keep === items[0]));
      for (const item of items) {
        if (item !== keep) item.setAttribute("data-dmgram-reel-extra", "");
      }
      scroller.setAttribute("data-dmgram-reel-scroller", "");
      reelScroller = scroller;
      armLock(viewerHost((keep.querySelector("video") || items[0].querySelector("video") || scroller)));
    }

    function armLock(host) {
      if (host && host !== document.documentElement && host !== document.body) {
        if (reelHost && reelHost !== host) reelHost.removeAttribute("data-dmgram-reel-host");
        host.setAttribute("data-dmgram-reel-host", "");
        reelHost = host;
      }
      document.documentElement.setAttribute("data-dmgram-reel-lock", "");
      if (!reelTouch) {
        reelTouch = blockGesture;
        document.addEventListener("touchmove", reelTouch, { capture: true, passive: false });
        document.addEventListener("wheel", reelTouch, { capture: true, passive: false });
      }
      if (!dmgram.reelLocked) {
        dmgram.reelLocked = true;
        window.__dmgramPost({ type: "reelLock", active: true });
      }
    }

    function viewportVideos() {
      return [...document.querySelectorAll("video")].filter((video) => {
        const rect = video.getBoundingClientRect();
        if (rect.width <= window.innerWidth * 0.8 || rect.height <= window.innerHeight * 0.7) return false;
        if (!intersectsViewport(rect)) return false;
        if (!painted(video)) return false;
        return !!video.currentSrc;
      });
    }

    function viewerHost(video) {
      let el = video.parentElement;
      let host = null;
      while (el && el !== document.body && el !== document.documentElement) {
        const rect = el.getBoundingClientRect();
        if (rect.width > window.innerWidth * 0.8 && rect.height > window.innerHeight * 0.7) host = el;
        el = el.parentElement;
      }
      return host;
    }

    function threadComposerVisible(host) {
      const boxes = document.querySelectorAll("[role='textbox'], textarea, [contenteditable='true']");
      for (const box of boxes) {
        if (host && host.contains(box)) continue;
        const rect = box.getBoundingClientRect();
        if (rect.width < 40 || rect.height < 8) continue;
        if (!intersectsViewport(rect) || !painted(box)) continue;
        const style = getComputedStyle(box);
        if (style.pointerEvents === "none" || style.visibility === "hidden") continue;
        const hit = document.elementFromPoint(rect.left + rect.width / 2, Math.min(rect.top + rect.height / 2, window.innerHeight - 1));
        if (!hit || (!box.contains(hit) && !hit.contains(box))) continue;
        return true;
      }
      return false;
    }

    function detectReel() {
      if (dmgram.route !== "DIRECT_THREAD") {
        unlockReel();
        return;
      }
      const videos = viewportVideos();
      const host = videos[0] ? viewerHost(videos[0]) : null;
      if (videos.length < 1 || threadComposerVisible(host)) {
        unlockReel();
        return;
      }
      let scroller = null;
      let el = videos[0].parentElement;
      while (el && el !== document.body) {
        if (el.scrollHeight > el.clientHeight * 1.5) {
          scroller = el;
          break;
        }
        el = el.parentElement;
      }
      if (scroller && videos.length >= 2) {
        lockReel(scroller);
        return;
      }
      armLock(host);
    }

    function scheduleReel() {
      if (reelCheckScheduled) return;
      reelCheckScheduled = true;
      requestAnimationFrame(() => {
        reelCheckScheduled = false;
        detectReel();
      });
    }

    function syncReelObserver() {
      const onThread = dmgram.route === "DIRECT_THREAD";
      if (onThread && !reelObserver && document.documentElement) {
        reelObserver = new MutationObserver(scheduleReel);
        reelObserver.observe(document.documentElement, { childList: true, subtree: true });
        scheduleReel();
      } else if (!onThread && reelObserver) {
        reelObserver.disconnect();
        reelObserver = null;
        unlockReel();
      }
    }

    function pointOf(rect) {
      return (rect.left + rect.width / 2) + "," + (rect.top + rect.height / 2) + "," + window.innerWidth;
    }

    function reelBackPoint() {
      const videos = viewportVideos();
      if (!videos.length) return "";
      const host = viewerHost(videos[0]) || document.documentElement;
      const icons = [...host.querySelectorAll("button svg, [role='button'] svg, a svg")];
      let best = null;
      let bestTop = Infinity;
      let bestLeft = Infinity;
      let bestLabel = 0;
      for (const svg of icons) {
        const rect = svg.getBoundingClientRect();
        if (rect.width < 8 || rect.height < 8 || rect.top < 0 || rect.top > 160) continue;
        if (!intersectsViewport(rect) || !painted(svg)) continue;
        const cx = rect.left + rect.width / 2;
        const cy = rect.top + rect.height / 2;
        const hit = document.elementFromPoint(cx, cy);
        if (!hit || (!svg.contains(hit) && !hit.contains(svg))) continue;
        const label = (svg.getAttribute("aria-label") || "").toLowerCase();
        const labelScore = label === "close" || label === "back" ? 1 : 0;
        const nearerTop = rect.top < bestTop - 1;
        const sameTop = Math.abs(rect.top - bestTop) <= 1;
        const nearerLeft = rect.left < bestLeft - 1;
        const sameLeft = Math.abs(rect.left - bestLeft) <= 1;
        if (best && !nearerTop && !(sameTop && nearerLeft) && !(sameTop && sameLeft && labelScore > bestLabel)) continue;
        best = rect;
        bestTop = rect.top;
        bestLeft = rect.left;
        bestLabel = labelScore;
      }
      return best ? pointOf(best) : "";
    }

    function reelEscape() {
      document.dispatchEvent(new KeyboardEvent("keydown", {
        key: "Escape",
        code: "Escape",
        keyCode: 27,
        which: 27,
        bubbles: true,
        cancelable: true,
      }));
    }

    dmgram.reelBackPoint = reelBackPoint;
    dmgram.reelEscape = reelEscape;
    dmgram.forceUnlock = unlockReel;

    reportRoute();
  }

  root.dmgramRouter = { classify, policy, install };
  if (typeof document !== "undefined" && typeof CONFIG !== "undefined" && root.__dmgram) {
    install();
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
