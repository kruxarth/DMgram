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
    const host = target.hostname.toLowerCase();
    if (host === "l.instagram.com") {
      return { class: "EXTERNAL", url: target.toString(), scheme: "https" };
    }
    const path = normalizePath(target.pathname);
    const reserved = new Set(((rules && rules.reserved) || []).map((name) => String(name).toLowerCase()));
    const routes = (rules && rules.routes) || [];
    for (const route of routes) {
      const hosts = route.hosts || [];
      const hostOk = hosts.length === 0 ? !!INSTAGRAM_HOSTS[host] : hosts.some((item) => item.toLowerCase() === host);
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

  function policy(tab, raw, rules) {
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
        decision = policy(CONFIG.tab, href, CONFIG.rules);
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
    let reelTouch = null;

    function blockGesture(event) {
      if (!document.documentElement || !document.documentElement.hasAttribute("data-dmgram-reel-lock")) return;
      event.preventDefault();
    }

    function unlockReel() {
      const html = document.documentElement;
      if (html) html.removeAttribute("data-dmgram-reel-lock");
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
      armLock();
    }

    function armLock() {
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
        return rect.width > window.innerWidth * 0.8 && rect.height > window.innerHeight * 0.7;
      });
    }

    function detectReel() {
      if (dmgram.route !== "DIRECT_THREAD") {
        unlockReel();
        return;
      }
      const videos = viewportVideos();
      if (videos.length < 1) {
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
      armLock();
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

    function reelBackPoint() {
      const video = [...document.querySelectorAll("video")].find((item) => {
        const rect = item.getBoundingClientRect();
        return rect.width > window.innerWidth * 0.8 && rect.height > window.innerHeight * 0.7;
      });
      if (!video) return "";
      const close = [...document.querySelectorAll('svg[aria-label="Close"]')].find((svg) => {
        const rect = svg.getBoundingClientRect();
        return rect.width >= 8 && rect.top >= 0 && rect.top < 160;
      });
      if (close) {
        const rect = close.getBoundingClientRect();
        return (rect.left + rect.width / 2) + "," + (rect.top + rect.height / 2) + "," + window.innerWidth;
      }
      const svgs = [...document.querySelectorAll('svg[aria-label="Back"]')];
      let best = null;
      let bestDepth = 99;
      for (const svg of svgs) {
        const rect = svg.getBoundingClientRect();
        if (rect.width < 8 || rect.top < 0 || rect.top > 180) continue;
        let el = svg.parentElement;
        let depth = 0;
        while (el && el !== document.body && el !== document.documentElement && depth < 16) {
          if (el.contains(video)) {
            if (depth < bestDepth) {
              bestDepth = depth;
              best = rect;
            }
            break;
          }
          el = el.parentElement;
          depth += 1;
        }
      }
      if (!best && svgs.length === 1) {
        const rect = svgs[0].getBoundingClientRect();
        if (rect.width >= 8 && rect.top >= 0 && rect.top < 180) best = rect;
      }
      if (!best) return "";
      return (best.left + best.width / 2) + "," + (best.top + best.height / 2) + "," + window.innerWidth;
    }

    dmgram.reelBackPoint = reelBackPoint;

    dmgram.closeReelViewer = function () {
      const video = [...document.querySelectorAll("video")].find((item) => {
        const rect = item.getBoundingClientRect();
        return rect.width > window.innerWidth * 0.8 && rect.height > window.innerHeight * 0.7;
      });
      if (!video) return;
      const svgs = [...document.querySelectorAll('svg[aria-label="Back"]')];
      let svg = null;
      let best = Infinity;
      for (const candidate of svgs) {
        const rect = candidate.getBoundingClientRect();
        if (rect.width < 8 || rect.top < 0 || rect.top > 160) continue;
        let el = candidate.parentElement;
        let host = null;
        for (let i = 0; i < 8 && el && el !== document.body && el !== document.documentElement; i++) {
          if (el.contains(video)) {
            const hostRect = el.getBoundingClientRect();
            if (hostRect.width > window.innerWidth * 0.8 && hostRect.height > window.innerHeight * 0.7) {
              host = el;
              break;
            }
          }
          el = el.parentElement;
        }
        if (!host || rect.top >= best) continue;
        best = rect.top;
        svg = candidate;
      }
      if (!svg) {
        if (window.__dmgramLog) window.__dmgramLog("info", "reel back not found");
        return;
      }
      const target = svg.closest("button, [role='button'], a") || svg.parentElement;
      if (target) target.click();
    };

    reportRoute();
  }

  root.dmgramRouter = { classify, policy, install };
  if (typeof document !== "undefined" && typeof CONFIG !== "undefined" && root.__dmgram) {
    install();
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
