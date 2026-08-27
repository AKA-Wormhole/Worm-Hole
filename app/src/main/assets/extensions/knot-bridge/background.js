// KNOT Page Bridge -- background script.
//
// Forwards app commands to page content scripts. GeckoView can have several
// sessions; the "active tab" query is not always the page the user is
// looking at, so we try every tab and pick the first real reply.

const NATIVE_APP_ID = "knot-bridge";
const ports = new Set();

function isUsefulReply(command, reply) {
  if (reply == null) return false;
  const text = String(reply);
  if (text === "SKIP" || text === "UNKNOWN_COMMAND") return false;
  if (command === "apply_translations" && text === "APPLIED:0") return false;
  if (command === "restore_originals" && text === "RESTORED:0") return false;
  return true;
}

async function sendToTabs(command, args) {
  const tabs = await browser.tabs.query({}).catch(() => []);
  let fallback = null;
  for (const tab of tabs) {
    if (tab.id == null) continue;
    try {
      const reply = await browser.tabs.sendMessage(tab.id, { command, args: args || {} });
      if (isUsefulReply(command, reply)) return reply;
      if (fallback == null) fallback = reply;
    } catch (_err) {
      try {
        await browser.tabs.executeScript(tab.id, { file: "content-script.js" });
        const reply = await browser.tabs.sendMessage(tab.id, { command, args: args || {} });
        if (isUsefulReply(command, reply)) return reply;
        if (fallback == null) fallback = reply;
      } catch (_injectErr) {
        // tab may be a privileged page without a content script
      }
    }
  }
  if (fallback != null) return fallback;
  throw new Error("NO_PAGE_BRIDGE");
}

async function handleMessage(port, message) {
  const { requestId, command, args } = message || {};
  if (!requestId || !command) return;

  try {
    const reply = await sendToTabs(command, args || {});
    port.postMessage({ requestId, ok: true, result: reply });
  } catch (err) {
    port.postMessage({
      requestId,
      ok: false,
      error: (err && err.message) || String(err),
    });
  }
}

function bindPort(port) {
  if (port.name !== NATIVE_APP_ID) return;
  ports.add(port);
  port.onMessage.addListener((msg) => handleMessage(port, msg));
  port.onDisconnect.addListener(() => ports.delete(port));
}

browser.runtime.onConnectNative.addListener(bindPort);
browser.runtime.onConnect.addListener(bindPort);
