// KNOT Page Bridge -- background script.
//
// Page commands prefer tabs.executeScript so they still work when the
// content-script message listener is missing. sendMessage is the fallback.

const NATIVE_APP_ID = "knot-bridge";
const ports = new Set();

function inlineCode(command, args) {
  const a = args || {};
  if (command === "read_page") {
    return "(function(){try{var n=document.querySelector('article');var t=(n&&n.innerText)||(document.body&&document.body.innerText)||'';return String(t).slice(0,12000);}catch(e){return '';}})()";
  }
  if (command === "scroll") {
    const dir = String(a.direction || "down").toLowerCase();
    const amount = parseInt(a.amount, 10) || 600;
    return "(function(){try{var d=" + JSON.stringify(dir) + ";var n=" + amount + ";if(d==='top'){window.scrollTo(0,0);}else if(d==='bottom'){window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}else if(d==='up'){window.scrollBy(0,-n);}else{window.scrollBy(0,n);}return 'SCROLLED';}catch(e){return 'ERR:'+e.message;}})()";
  }
  if (command === "find_text") {
    return "(function(){try{var q=" + JSON.stringify(String(a.query || "")) + ";var t=(document.body&&document.body.innerText)||'';return t.toLowerCase().indexOf(String(q).toLowerCase())>=0?('Found \\''+q+'\\''):('\\''+q+'\\' not found');}catch(e){return 'ERR:'+e.message;}})()";
  }
  if (command === "get_page_info") {
    return "(function(){try{return JSON.stringify({title:document.title||'',url:location.href||''});}catch(e){return '{}';}})()";
  }
  return null;
}

function isUsefulReply(command, reply) {
  if (reply == null) return false;
  const text = String(reply);
  if (text === "SKIP" || text === "UNKNOWN_COMMAND" || text === "") return false;
  if (command === "apply_translations" && text === "APPLIED:0") return false;
  if (command === "restore_originals" && text === "RESTORED:0") return false;
  return true;
}

function isHttpTab(tab) {
  const url = tab && tab.url ? String(tab.url) : "";
  return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:");
}

async function executeOnTabs(command, args) {
  const code = inlineCode(command, args);
  if (!code) return null;
  const tabs = await browser.tabs.query({}).catch(() => []);
  for (const tab of tabs) {
    if (tab.id == null || !isHttpTab(tab)) continue;
    try {
      const results = await browser.tabs.executeScript(tab.id, { code: code, runAt: "document_idle" });
      const reply = results && results.length ? results[0] : null;
      if (isUsefulReply(command, reply)) return reply;
    } catch (_err) {
    }
  }
  return null;
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
      }
    }
  }
  return fallback;
}

async function handleMessage(port, message) {
  const { requestId, command, args } = message || {};
  if (!requestId || !command) return;

  try {
    const injected = await executeOnTabs(command, args || {});
    if (injected != null) {
      port.postMessage({ requestId, ok: true, result: injected });
      return;
    }
    const reply = await sendToTabs(command, args || {});
    if (reply != null) {
      port.postMessage({ requestId, ok: true, result: reply });
      return;
    }
    throw new Error("NO_PAGE_BRIDGE");
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
