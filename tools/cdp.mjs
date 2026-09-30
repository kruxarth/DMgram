#!/usr/bin/env node
// DevTools CLI for DMGram's live WebViews. No npm dependencies.

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import net from "node:net";
import process from "node:process";

const PREFERRED_PORT = 9222;
const PACKAGE = "app.dmgram.debug";
const USAGE = `Usage:
  node tools/cdp.mjs targets
  node tools/cdp.mjs eval '<js>' [--all]
  node tools/cdp.mjs css <file> [--id <name>]

css writes the file into <style id="dmgram-<name>"> in every WebView.
The id defaults to "live".`;

function fail(message) {
  console.error(message);
  process.exit(1);
}

function adb(args, { allowFailure = false } = {}) {
  try {
    return execFileSync("adb", args, {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch (error) {
    if (allowFailure) return "";
    if (error.code === "ENOENT") fail("adb was not found on PATH.");
    const stderr = error.stderr?.toString().trim() ?? "";
    const stdout = error.stdout?.toString().trim() ?? "";
    fail(`adb ${args.join(" ")} failed.\n${stderr || stdout || error.message}`);
  }
}

function ensureDevice() {
  const out = adb(["devices"]);
  const rows = out
    .split("\n")
    .slice(1)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [serial, state] = line.split(/\s+/);
      return { serial, state };
    });
  const online = rows.filter((row) => row.state === "device");
  if (online.length === 0) {
    if (rows.some((row) => row.state === "unauthorized")) {
      fail("A device is connected but unauthorized. Accept the USB debugging prompt on the phone.");
    }
    if (rows.some((row) => row.state === "offline")) {
      fail("A device is connected but offline. Reconnect USB or wireless debugging.");
    }
    fail("No device. Connect the phone and enable USB debugging.");
  }
  if (online.length > 1 && !process.env.ANDROID_SERIAL) {
    fail(
      `More than one device is connected:\n${online.map((row) => row.serial).join("\n")}\nSet ANDROID_SERIAL to choose one.`,
    );
  }
}

function portFree(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once("error", () => resolve(false));
    server.once("listening", () => server.close(() => resolve(true)));
    server.listen(port, "127.0.0.1");
  });
}

function ensureForward() {
  ensureDevice();
  let pid = "";
  try {
    pid = execFileSync("adb", ["shell", "pidof", PACKAGE], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch (error) {
    const stderr = error.stderr?.toString().trim() ?? "";
    if (error.status === 1 && !stderr) {
      fail(`DMGram is not running (${PACKAGE}). Open the debug app on the phone.`);
    }
    fail(`Could not read the app process id.\n${stderr || error.message}`);
  }
  const pids = pid.split(/\s+/).filter(Boolean);
  if (pids.length !== 1) {
    fail(`Expected one process for ${PACKAGE}, found: ${pid}`);
  }
  return pids[0];
}

async function choosePort(pid) {
  const socket = `localabstract:webview_devtools_remote_${pid}`;
  for (let port = PREFERRED_PORT; port < PREFERRED_PORT + 20; port++) {
    adb(["forward", "--remove", `tcp:${port}`], { allowFailure: true });
    if (!(await portFree(port))) continue;
    try {
      execFileSync("adb", ["forward", `tcp:${port}`, socket], { stdio: ["ignore", "pipe", "pipe"] });
      if (port !== PREFERRED_PORT) {
        console.error(`Port ${PREFERRED_PORT} is in use on this computer. Using ${port}.`);
      }
      return port;
    } catch (error) {
      const stderr = error.stderr?.toString().trim() ?? "";
      if (!/Address already in use/i.test(stderr)) {
        fail(`adb forward tcp:${port} ${socket} failed.\n${stderr || error.message}`);
      }
    }
  }
  fail(`Could not bind a local DevTools port from ${PREFERRED_PORT} to ${PREFERRED_PORT + 19}.`);
}

async function listTargets(port) {
  let response;
  try {
    response = await fetch(`http://127.0.0.1:${port}/json`);
  } catch (error) {
    fail(
      `Could not reach WebView DevTools on port ${port}. Is the debug app in the foreground?\n${error.message}`,
    );
  }
  if (!response.ok) fail(`DevTools endpoint returned HTTP ${response.status}.`);
  const all = await response.json();
  const pages = all.filter((target) => target.type === "page" && target.webSocketDebuggerUrl);
  if (pages.length === 0) {
    fail("No WebView targets. Open DMGram and make sure this is a debug build.");
  }
  return pages.map((target) => {
    let visible = null;
    try {
      const description = JSON.parse(target.description || "{}");
      visible = typeof description.visible === "boolean" ? description.visible : null;
    } catch {
      visible = null;
    }
    return {
      id: target.id,
      url: target.url,
      title: target.title,
      visible,
      webSocketDebuggerUrl: target.webSocketDebuggerUrl,
    };
  });
}

function messageText(data) {
  if (typeof data === "string") return data;
  if (Buffer.isBuffer(data)) return data.toString("utf8");
  if (data instanceof ArrayBuffer) return Buffer.from(data).toString("utf8");
  if (typeof data?.text === "function") return data.text();
  return String(data);
}

function evaluate(wsUrl, expression) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error("Timed out waiting for the WebView to evaluate."));
    }, 30000);
    ws.addEventListener("error", () => {
      clearTimeout(timer);
      reject(new Error("WebSocket connection to the WebView failed."));
    });
    ws.addEventListener("open", () => {
      ws.send(JSON.stringify({
        id: 1,
        method: "Runtime.evaluate",
        params: { expression, returnByValue: true, awaitPromise: true },
      }));
    });
    ws.addEventListener("message", async (event) => {
      let msg;
      try {
        msg = JSON.parse(await messageText(event.data));
      } catch {
        return;
      }
      if (msg.id !== 1) return;
      clearTimeout(timer);
      ws.close();
      if (msg.error) {
        reject(new Error(msg.error.message || JSON.stringify(msg.error)));
        return;
      }
      resolve(msg.result);
    });
  });
}

function valueOf(result) {
  if (result.exceptionDetails) {
    const details = result.exceptionDetails;
    const text = details.exception?.description || details.text || "Evaluation failed";
    const error = new Error(text);
    error.details = details;
    throw error;
  }
  const remote = result.result ?? {};
  if (Object.prototype.hasOwnProperty.call(remote, "value")) return remote.value;
  return { type: remote.type ?? null, description: remote.description ?? null };
}

function parseArgs(argv) {
  const command = argv[0];
  if (!command || command === "--help" || command === "-h") return { command: "help" };
  const flags = { all: false, id: "live" };
  const positionals = [];
  for (let i = 1; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--all") {
      flags.all = true;
    } else if (arg === "--id") {
      const value = argv[++i];
      if (!value || value.startsWith("--")) fail("css --id needs a name, for example --id polish.");
      if (!/^[A-Za-z0-9_-]+$/.test(value)) fail("css --id may only contain letters, numbers, _ and -.");
      flags.id = value;
    } else if (arg.startsWith("--")) {
      fail(`Unknown option ${arg}.\n${USAGE}`);
    } else {
      positionals.push(arg);
    }
  }
  return { command, flags, positionals };
}

function visibleTargets(targets) {
  const visible = targets.filter((target) => target.visible === true);
  if (visible.length === 0) {
    fail(
      "No visible WebView target.\n" +
        targets.map((target) => `${target.id}\tvisible=${target.visible}\t${target.url}`).join("\n"),
    );
  }
  return visible;
}

async function main() {
  const { command, flags, positionals } = parseArgs(process.argv.slice(2));
  if (command === "help") {
    console.log(USAGE);
    return;
  }
  if (!["targets", "eval", "css"].includes(command)) fail(`Unknown command "${command}".\n${USAGE}`);

  let css = "";
  if (command === "css") {
    if (positionals.length !== 1) fail(`css needs one file.\n${USAGE}`);
    try {
      css = readFileSync(positionals[0], "utf8");
    } catch (error) {
      fail(`Could not read ${positionals[0]}.\n${error.message}`);
    }
  }

  const port = await choosePort(ensureForward());
  const targets = await listTargets(port);

  if (command === "targets") {
    console.log(JSON.stringify(targets.map(({ id, url, visible }) => ({ id, url, visible }))));
    return;
  }

  if (command === "eval") {
    if (positionals.length !== 1) fail(`eval needs one JavaScript expression.\n${USAGE}`);
    const chosen = flags.all ? targets : [visibleTargets(targets)[0]];
    if (!flags.all && visibleTargets(targets).length > 1) {
      console.error(`Multiple visible targets; using ${chosen[0].id}.`);
    }
    const results = [];
    let failed = false;
    for (const target of chosen) {
      try {
        const value = valueOf(await evaluate(target.webSocketDebuggerUrl, positionals[0]));
        results.push(flags.all ? { id: target.id, url: target.url, value } : value);
      } catch (error) {
        failed = true;
        const payload = { id: target.id, url: target.url, error: error.message };
        if (flags.all) results.push(payload);
        else {
          console.error(JSON.stringify(payload));
          process.exit(1);
        }
      }
    }
    console.log(JSON.stringify(flags.all ? results : results[0]));
    if (failed) process.exit(1);
    return;
  }

  const expression = `(() => {
    const id = ${JSON.stringify(`dmgram-${flags.id}`)};
    const css = ${JSON.stringify(css)};
    let el = document.getElementById(id);
    if (!el) {
      el = document.createElement("style");
      el.id = id;
      (document.head || document.documentElement).appendChild(el);
    }
    el.textContent = css;
    return { id, bytes: css.length };
  })()`;
  const results = [];
  let failed = false;
  for (const target of targets) {
    try {
      results.push({
        id: target.id,
        url: target.url,
        value: valueOf(await evaluate(target.webSocketDebuggerUrl, expression)),
      });
    } catch (error) {
      failed = true;
      results.push({ id: target.id, url: target.url, error: error.message });
    }
  }
  console.log(JSON.stringify(results));
  if (failed) process.exit(1);
}

main().catch((error) => fail(error.stack || error.message));
