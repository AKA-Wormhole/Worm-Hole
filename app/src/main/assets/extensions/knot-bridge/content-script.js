// KNOT Page Bridge -- content script.
//
// Runs in every page (document_idle) and implements the actual DOM
// operations the agent asks for. The background script forwards a command
// here via browser.tabs.sendMessage / browser.runtime.onMessage, and
// whatever this returns goes straight back to the Kotlin side as the tool
// result. This is the real replacement for the old GeckoJs reflective probe:
// every one of these mirrors a script BrowserAgent.kt used to build by hand.

(function () {
  "use strict";

  function readPage() {
    try {
      const article = document.querySelector("article");
      const text = article ? article.innerText : document.body ? document.body.innerText : "";
      return (text || "").substring(0, 12000);
    } catch (e) {
      return "";
    }
  }

  function bodyText() {
    try {
      return document.body ? document.body.innerText : "";
    } catch (e) {
      return "";
    }
  }

  function findText(query) {
    const text = bodyText();
    const found = !!query && text.toLowerCase().indexOf(String(query).toLowerCase()) >= 0;
    return found ? "Found '" + query + "'" : "'" + query + "' not found";
  }

  function tap(selector, text) {
    let el = null;
    if (selector) {
      el = document.querySelector(selector);
    } else if (text) {
      const target = String(text).toLowerCase();
      const all = document.querySelectorAll(
        'a,button,input,[role="button"],[onclick],label,summary',
      );
      for (let i = 0; i < all.length; i++) {
        const t = (all[i].innerText || all[i].value || all[i].getAttribute("aria-label") || "")
          .trim()
          .toLowerCase();
        if (t.indexOf(target) >= 0) {
          el = all[i];
          break;
        }
      }
    }
    if (!el) return "NOT_FOUND";
    el.scrollIntoView({ block: "center", behavior: "instant" });
    el.click();
    return "CLICKED";
  }

  function typeText(value, selector) {
    let el = null;
    if (selector) {
      el = document.querySelector(selector);
      if (!el) return "NOT_FOUND";
    } else {
      el = document.activeElement;
      if (!el || (el.tagName !== "INPUT" && el.tagName !== "TEXTAREA" && !el.isContentEditable)) {
        el = document.querySelector("input:not([type=hidden]),textarea,[contenteditable=true]");
      }
      if (!el) return "NO_INPUT";
    }
    el.focus();
    if (el.isContentEditable) {
      el.innerText = value;
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    return "TYPED";
  }

  function scroll(direction, amount) {
    const amt = amount || 600;
    switch (direction) {
      case "top":
        window.scrollTo(0, 0);
        break;
      case "bottom":
        window.scrollTo(0, document.body.scrollHeight);
        break;
      case "up":
        window.scrollBy(0, -amt);
        break;
      default:
        window.scrollBy(0, amt);
    }
    return "ok";
  }

  function editPage(find, replace, selector) {
    if (!find) return "NO_ROOT";
    const root = (selector && document.querySelector(selector)) || document.body;
    if (!root) return "NO_ROOT";
    let count = 0;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    let node;
    while ((node = walker.nextNode())) {
      if (node.nodeValue && node.nodeValue.indexOf(find) >= 0) {
        node.nodeValue = node.nodeValue.split(find).join(replace);
        count++;
      }
    }
    return "EDITED:" + count;
  }

  function selectText(text) {
    if (!text) return "NOT_FOUND";
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    let node;
    while ((node = walker.nextNode())) {
      const idx = (node.nodeValue || "").indexOf(text);
      if (idx >= 0) {
        const range = document.createRange();
        range.setStart(node, idx);
        range.setEnd(node, idx + text.length);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        return "SELECTED";
      }
    }
    return "NOT_FOUND";
  }

  function getSelectionText() {
    try {
      return window.getSelection ? window.getSelection().toString() : "";
    } catch (e) {
      return "";
    }
  }

  function setDesktopViewport(desktop) {
    const meta = document.querySelector('meta[name="viewport"]');
    if (meta) {
      meta.setAttribute(
        "content",
        desktop ? "width=1280" : "width=device-width, initial-scale=1",
      );
    }
    return "ok";
  }

  function getPageInfo() {
    return {
      title: document.title || "",
      url: location.href,
    };
  }

  function collectTextNodes(limit) {
    const max = parseInt(limit, 10) || 360;
    const nodes = [];
    window.__whTranslateNodes = [];
    window.__whTranslateOriginals = [];
    if (!document.body) return JSON.stringify(nodes);
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (node) {
        if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
        const parent = node.parentElement;
        if (!parent) return NodeFilter.FILTER_REJECT;
        const tag = parent.tagName;
        if (
          tag === "SCRIPT" ||
          tag === "STYLE" ||
          tag === "NOSCRIPT" ||
          tag === "TEXTAREA" ||
          tag === "INPUT" ||
          tag === "CODE" ||
          tag === "PRE"
        ) {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
      },
    });
    let node;
    while ((node = walker.nextNode()) && nodes.length < max) {
      const text = node.nodeValue;
      if (!text || text.trim().length < 2) continue;
      window.__whTranslateNodes.push(node);
      window.__whTranslateOriginals.push(text);
      nodes.push({ id: nodes.length, text: text });
    }
    return JSON.stringify(nodes);
  }

  function applyTranslations(pairs) {
    let list = pairs;
    if (typeof pairs === "string") {
      try {
        list = JSON.parse(pairs);
      } catch (e) {
        return "ERR:BAD_PAIRS";
      }
    }
    const stored = window.__whTranslateNodes || [];
    let count = 0;
    if (!Array.isArray(list)) return "APPLIED:0";
    for (let i = 0; i < list.length; i++) {
      const item = list[i] || {};
      const node = stored[item.id];
      if (node && typeof item.text === "string") {
        node.nodeValue = item.text;
        count++;
      }
    }
    return "APPLIED:" + count;
  }

  function restoreOriginals() {
    const stored = window.__whTranslateNodes || [];
    const originals = window.__whTranslateOriginals || [];
    let count = 0;
    for (let i = 0; i < stored.length && i < originals.length; i++) {
      if (stored[i]) {
        stored[i].nodeValue = originals[i];
        count++;
      }
    }
    return "RESTORED:" + count;
  }

  function detectScript(sample) {
    const counts = { ja: 0, ko: 0, zh: 0, ar: 0, ru: 0, hi: 0, th: 0, he: 0, el: 0, latin: 0 };
    for (let i = 0; i < sample.length; i++) {
      const c = sample.charCodeAt(i);
      if (c >= 0x3040 && c <= 0x30ff) counts.ja++;
      else if (c >= 0xac00 && c <= 0xd7af) counts.ko++;
      else if (c >= 0x4e00 && c <= 0x9fff) counts.zh++;
      else if (c >= 0x0600 && c <= 0x06ff) counts.ar++;
      else if (c >= 0x0400 && c <= 0x04ff) counts.ru++;
      else if (c >= 0x0900 && c <= 0x097f) counts.hi++;
      else if (c >= 0x0e00 && c <= 0x0e7f) counts.th++;
      else if (c >= 0x0590 && c <= 0x05ff) counts.he++;
      else if (c >= 0x0370 && c <= 0x03ff) counts.el++;
      else if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) counts.latin++;
    }
    let best = "und";
    let bestCount = 0;
    Object.keys(counts).forEach(function (key) {
      if (key === "latin") return;
      if (counts[key] > bestCount) {
        best = key;
        bestCount = counts[key];
      }
    });
    if (bestCount >= 8) return best;
    return "und";
  }

  function detectLanguage() {
    const htmlLang = (
      document.documentElement.lang ||
      document.documentElement.getAttribute("xml:lang") ||
      ""
    )
      .toLowerCase()
      .split("-")[0];
    const sample = ((document.body && document.body.innerText) || "")
      .replace(/\s+/g, " ")
      .slice(0, 2500);
    const script = detectScript(sample);
    let code = htmlLang;
    let confident = true;
    if (!code || code === "und") {
      code = script;
      confident = script !== "und";
    } else if (script !== "und" && script !== "en" && code === "en") {
      code = script;
      confident = true;
    }
    if (!code) code = "und";
    return JSON.stringify({ code: code, confident: confident, sample: sample.slice(0, 240) });
  }

  // execute_js's safety-list is still enforced Kotlin-side (blocked terms are
  // filtered before this ever runs) -- this is just the sandboxed evaluator.
  function runExpression(code) {
    try {
      // eslint-disable-next-line no-new-func
      const fn = new Function('"use strict"; return (' + code + ");");
      const result = fn();
      return String(result);
    } catch (e) {
      return "ERR:" + (e && e.message ? e.message : String(e));
    }
  }

  browser.runtime.onMessage.addListener((message) => {
    const { command, args } = message || {};
    const a = args || {};
    switch (command) {
      case "get_page_info":
        return Promise.resolve(getPageInfo());
      case "read_page":
        return Promise.resolve(readPage());
      case "find_text":
        return Promise.resolve(findText(a.query));
      case "tap":
        return Promise.resolve(tap(a.selector, a.text));
      case "type_text":
        return Promise.resolve(typeText(a.text, a.selector));
      case "scroll":
        return Promise.resolve(scroll(a.direction, a.amount));
      case "edit_page":
        return Promise.resolve(editPage(a.find, a.replace, a.selector));
      case "select_text":
        return Promise.resolve(selectText(a.text));
      case "get_selection":
        return Promise.resolve(getSelectionText());
      case "set_desktop_viewport":
        return Promise.resolve(setDesktopViewport(a.desktop === true || a.desktop === "true" || a.desktop === 1 || a.desktop === "1"));
      case "collect_text_nodes":
        return Promise.resolve(collectTextNodes(a.limit));
      case "apply_translations":
        return Promise.resolve(applyTranslations(a.pairs));
      case "restore_originals":
        return Promise.resolve(restoreOriginals());
      case "detect_language":
        return Promise.resolve(detectLanguage());
      case "execute_js":
        return Promise.resolve(runExpression(a.code));
      default:
        return Promise.resolve("UNKNOWN_COMMAND");
    }
  });
})();
