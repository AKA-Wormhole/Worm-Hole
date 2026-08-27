// KNOT Page Bridge -- background script.
//
// Page commands prefer tabs.executeScript so they still work when the
// content-script message listener is missing. sendMessage is the fallback.

const NATIVE_APP_ID = "knot-bridge";
const ports = new Set();
const boundPorts = new WeakSet();

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
  if (command === "collect_text_nodes") {
    const limit = parseInt(a.limit, 10) || 360;
    return "(function(){try{var max=" + limit + ";var nodes=[];if(!document.body)return '[]';var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){if(!n.nodeValue||!n.nodeValue.trim())return NodeFilter.FILTER_REJECT;var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var t=p.tagName;if(t==='SCRIPT'||t==='STYLE'||t==='NOSCRIPT'||t==='TEXTAREA'||t==='INPUT'||t==='CODE'||t==='PRE')return NodeFilter.FILTER_REJECT;return NodeFilter.FILTER_ACCEPT;}});var n;while((n=w.nextNode())&&nodes.length<max){var tx=n.nodeValue;if(tx&&tx.trim().length>=2)nodes.push({id:nodes.length,text:tx});}return JSON.stringify(nodes);}catch(e){return '[]';}})()";
  }
  if (command === "apply_translations") {
    var pairs = a.pairs;
    if (typeof pairs === "string") {
      try { pairs = JSON.parse(pairs); } catch (e) { pairs = []; }
    }
    return "(function(){try{var pairs=" + JSON.stringify(pairs || []) + ";var max=360;var nodes=[];if(!document.body)return 'APPLIED:0';var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){if(!n.nodeValue||!n.nodeValue.trim())return NodeFilter.FILTER_REJECT;var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var t=p.tagName;if(t==='SCRIPT'||t==='STYLE'||t==='NOSCRIPT'||t==='TEXTAREA'||t==='INPUT'||t==='CODE'||t==='PRE')return NodeFilter.FILTER_REJECT;return NodeFilter.FILTER_ACCEPT;}});var n;while((n=w.nextNode())&&nodes.length<max){var tx=n.nodeValue;if(tx&&tx.trim().length>=2)nodes.push(n);}var c=0;if(!Array.isArray(pairs))return 'APPLIED:0';for(var i=0;i<pairs.length;i++){var item=pairs[i]||{};var node=nodes[item.id];if(node&&typeof item.text==='string'){node.nodeValue=item.text;c++;}}return 'APPLIED:'+c;}catch(e){return 'ERR:'+e.message;}})()";
  }
  if (command === "restore_originals") {
    return "'RESTORED:0'";
  }
  if (command === "apply_full_text") {
    return "(function(){try{var t=" + JSON.stringify(String(a.text || "")) + ";var el=document.querySelector('article')||document.body;if(!el||!t)return 'APPLIED:0';el.innerText=t;return 'APPLIED_FULL';}catch(e){return 'ERR:'+e.message;}})()";
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
  if (!port || port.name !== NATIVE_APP_ID) return;
  if (boundPorts.has(port)) return;
  boundPorts.add(port);
  ports.add(port);
  port.onMessage.addListener((msg) => handleMessage(port, msg));
  port.onDisconnect.addListener(() => ports.delete(port));
}

browser.runtime.onConnectNative.addListener(bindPort);
browser.runtime.onConnect.addListener(bindPort);

function connectNative() {
  try {
    const port = browser.runtime.connectNative(NATIVE_APP_ID);
    bindPort(port);
    port.onDisconnect.addListener(function () {
      ports.delete(port);
      setTimeout(connectNative, 500);
    });
  } catch (_e) {
    setTimeout(connectNative, 800);
  }
}
connectNative();

const LINGVA_HOSTS = [
  "https://lingva.ml",
  "https://lingva.garudalinux.org",
  "https://translate.plausibility.cloud",
];
const LIBRE_HOSTS = [
  "https://translate.fedilab.app",
  "https://translate.cutie.dating",
  "https://libretranslate.com",
];

async function translateOneLingva(text, target) {
  const encoded = encodeURIComponent(text);
  for (let i = 0; i < LINGVA_HOSTS.length; i++) {
    try {
      const res = await fetch(LINGVA_HOSTS[i] + "/api/v1/auto/" + target + "/" + encoded);
      if (!res.ok) continue;
      const json = await res.json();
      if (json && json.translation) return String(json.translation);
    } catch (_e) {}
  }
  return null;
}

async function translateOneLibre(text, target) {
  for (let i = 0; i < LIBRE_HOSTS.length; i++) {
    try {
      const res = await fetch(LIBRE_HOSTS[i] + "/translate", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ q: text, source: "auto", target: target, format: "text" }),
      });
      if (!res.ok) continue;
      const json = await res.json();
      if (json && json.translatedText) return String(json.translatedText);
    } catch (_e) {}
  }
  return null;
}

async function translateBatch(texts, target) {
  const out = [];
  const lang = String(target || "en").toLowerCase().split("-")[0] || "en";
  for (let i = 0; i < texts.length; i++) {
    const original = String(texts[i] || "");
    const translated =
      (await translateOneLingva(original, lang)) ||
      (await translateOneLibre(original, lang)) ||
      original;
    out.push(translated);
  }
  return out;
}

browser.runtime.onMessage.addListener(function (message) {
  if (!message || message.cmd !== "translate_batch") return;
  const texts = Array.isArray(message.texts) ? message.texts.slice(0, 40) : [];
  return translateBatch(texts, message.target);
});
