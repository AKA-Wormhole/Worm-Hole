(function () {
  if (window.__wormholeWebAuthnInstalled) return;
  window.__wormholeWebAuthnInstalled = true;

  var nativeCredentials = navigator.credentials;
  var pending = {};
  var nextRequestId = 1;

  function toBase64Url(buffer) {
    var bytes = new Uint8Array(buffer);
    var binary = "";
    for (var i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function fromBase64Url(value) {
    var padded = value.replace(/-/g, "+").replace(/_/g, "/");
    while (padded.length % 4) padded += "=";
    var binary = atob(padded);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  function encodeBuffers(value) {
    if (value == null) return value;
    if (value instanceof ArrayBuffer) return { __wormholeBuffer: true, b64: toBase64Url(value) };
    if (ArrayBuffer.isView(value)) return { __wormholeBuffer: true, b64: toBase64Url(value.buffer) };
    if (Array.isArray(value)) return value.map(encodeBuffers);
    if (typeof value === "object") {
      var out = {};
      for (var key in value) {
        if (Object.prototype.hasOwnProperty.call(value, key)) out[key] = encodeBuffers(value[key]);
      }
      return out;
    }
    return value;
  }

  function decodeBuffers(value) {
    if (value == null) return value;
    if (typeof value === "object" && value.__wormholeBuffer === true) return fromBase64Url(value.b64);
    if (Array.isArray(value)) return value.map(decodeBuffers);
    if (typeof value === "object") {
      var out = {};
      for (var key in value) {
        if (Object.prototype.hasOwnProperty.call(value, key)) out[key] = decodeBuffers(value[key]);
      }
      return out;
    }
    return value;
  }

  window.__wormholeWebAuthnResolve = function (requestId, resultJson) {
    var entry = pending[requestId];
    if (!entry) return;
    delete pending[requestId];
    try {
      var result = JSON.parse(resultJson);
      entry.resolve(decodeBuffers(result));
    } catch (e) {
      entry.reject(e);
    }
  };

  window.__wormholeWebAuthnReject = function (requestId, name, message) {
    var entry = pending[requestId];
    if (!entry) return;
    delete pending[requestId];
    var error;
    try {
      error = new DOMException(message || name, name || "NotAllowedError");
    } catch (e) {
      error = new Error(message || name);
      error.name = name || "NotAllowedError";
    }
    entry.reject(error);
  };

  function bridgeCall(method, publicKey, signal) {
    return new Promise(function (resolve, reject) {
      if (typeof WormHoleWebAuthn === "undefined") {
        reject(new DOMException("WebAuthn is not available", "NotSupportedError"));
        return;
      }
      var requestId = String(nextRequestId++);
      pending[requestId] = { resolve: resolve, reject: reject };

      if (signal) {
        if (signal.aborted) {
          delete pending[requestId];
          reject(new DOMException("The operation was aborted", "AbortError"));
          return;
        }
        signal.addEventListener("abort", function () {
          if (!pending[requestId]) return;
          delete pending[requestId];
          WormHoleWebAuthn.cancel(requestId);
          reject(new DOMException("The operation was aborted", "AbortError"));
        });
      }

      var payload = JSON.stringify(encodeBuffers(publicKey));
      try {
        if (method === "create") {
          WormHoleWebAuthn.create(requestId, payload, window.location.origin);
        } else {
          WormHoleWebAuthn.get(requestId, payload, window.location.origin);
        }
      } catch (e) {
        delete pending[requestId];
        reject(new DOMException(String(e), "NotAllowedError"));
      }
    });
  }

  var polyfillCredentials = {
    create: function (options) {
      if (!options || !options.publicKey) {
        return nativeCredentials
          ? nativeCredentials.create(options)
          : Promise.reject(new DOMException("Not supported", "NotSupportedError"));
      }
      return bridgeCall("create", options.publicKey, options.signal);
    },
    get: function (options) {
      if (!options || !options.publicKey) {
        return nativeCredentials
          ? nativeCredentials.get(options)
          : Promise.reject(new DOMException("Not supported", "NotSupportedError"));
      }
      return bridgeCall("get", options.publicKey, options.signal);
    },
    store: nativeCredentials ? nativeCredentials.store.bind(nativeCredentials) : undefined,
    preventSilentAccess: nativeCredentials
      ? nativeCredentials.preventSilentAccess.bind(nativeCredentials)
      : undefined,
  };

  try {
    Object.defineProperty(navigator, "credentials", {
      configurable: true,
      enumerable: true,
      get: function () {
        return polyfillCredentials;
      },
    });
  } catch (e) {
  }

  if (typeof PublicKeyCredential !== "undefined") {
    PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function () {
      return Promise.resolve(true);
    };
    if (typeof PublicKeyCredential.isConditionalMediationAvailable !== "function") {
      PublicKeyCredential.isConditionalMediationAvailable = function () {
        return Promise.resolve(false);
      };
    }
  } else {
    window.PublicKeyCredential = function () {};
    window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function () {
      return Promise.resolve(true);
    };
    window.PublicKeyCredential.isConditionalMediationAvailable = function () {
      return Promise.resolve(false);
    };
  }
})();
