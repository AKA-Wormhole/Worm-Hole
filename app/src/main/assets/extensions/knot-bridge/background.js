// KNOT Page Bridge -- background script.
//
// GeckoView has no public "evaluateJS(script)" call on GeckoSession (see
// GeckoJs.kt for the long version of why). The supported way to reach page
// content is: bundle a WebExtension, run a content script in every page,
// and talk to the app over a native-messaging Port
// (browser.runtime.connectNative <-> GeckoSession.WebExtensionController /
// WebExtension.MessageDelegate on the Kotlin side).
//
// This background script is the hub: it keeps one native port open to the
// app (native app id "knot-bridge"), and forwards each command it receives
// to the content script running in the *currently active tab* of the
// window the port belongs to, then relays the content script's reply back
// over the same port. Kotlin's GeckoExtensionBridge correlates replies by
// requestId.

const NATIVE_APP_ID = "knot-bridge";

// One native port per GeckoSession (each session is its own "browser" tab
// from the extension's point of view). Keyed by the port object itself.
const ports = new Set();

function activeTabIdForPort() {
  // In GeckoView's single-session-per-extension-context model there is
  // effectively one foreground tab per session, so just ask for the active
  // tab in the current window.
  return browser.tabs
    .query({ active: true, currentWindow: true })
    .then((tabs) => (tabs && tabs[0] ? tabs[0].id : null));
}

async function handleMessage(port, message) {
  const { requestId, command, args } = message || {};
  if (!requestId || !command) return;

  try {
    const tabId = await activeTabIdForPort();
    if (tabId == null) {
      port.postMessage({ requestId, ok: false, error: "NO_ACTIVE_TAB" });
      return;
    }
    const reply = await browser.tabs.sendMessage(tabId, { command, args: args || {} });
    port.postMessage({ requestId, ok: true, result: reply });
  } catch (err) {
    port.postMessage({
      requestId,
      ok: false,
      error: (err && err.message) || String(err),
    });
  }
}

browser.runtime.onConnectNative.addListener((port) => {
  if (port.name !== NATIVE_APP_ID) return;
  ports.add(port);
  port.onMessage.addListener((msg) => handleMessage(port, msg));
  port.onDisconnect.addListener(() => ports.delete(port));
});

// Some GeckoView builds/hosts deliver the app<->extension channel via the
// generic runtime.onConnect (message delegate keyed by native-app id) rather
// than onConnectNative specifically. Listen on both so the bridge works
// across GeckoView versions without needing to guess which one a given
// build implements.
browser.runtime.onConnect.addListener((port) => {
  if (port.name !== NATIVE_APP_ID) return;
  ports.add(port);
  port.onMessage.addListener((msg) => handleMessage(port, msg));
  port.onDisconnect.addListener(() => ports.delete(port));
});
