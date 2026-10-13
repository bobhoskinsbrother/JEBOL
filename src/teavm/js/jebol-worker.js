// The worker a JEBOL page runs its interpreter in. The shell is the only
// JavaScript here: it loads the interpreter and speaks for the page. The
// script to run arrives as a {run: ...} message and goes straight to the
// interpreter; everything else the page posts -- clicks, keys, how big the
// viewport is -- waits in a queue the interpreter pulls between slices of a
// wait, on its own thread, and nothing here ever re-enters the module while
// it is suspended.
//
// Anything that goes wrong loading or booting is posted to the page as a
// failed message, because a worker that died quietly would leave a page
// waiting forever.
importScripts('jebol-web-runtime.js');

var inbox = [];
var jebol = null;

onmessage = function (event) {
  var said = event.data;
  if (said && typeof said.run === 'string') {
    if (jebol) {
      jebol.runTheScript(said.run);
    } else {
      self.postMessage({ kind: 'failed', text: 'the interpreter was not ready to run a script' });
    }
    return;
  }
  inbox.push(JSON.stringify(said));
};

self.addEventListener('unhandledrejection', function (event) {
  var why = event.reason && event.reason.stack ? event.reason.stack : String(event.reason);
  self.postMessage({ kind: 'failed', text: 'the worker dropped a promise: ' + why });
});

self.addEventListener('error', function (event) {
  var detail = event.message || String(event.error);
  if (event.error && event.error.stack) {
    detail = detail + '\n' + event.error.stack;
  }
  self.postMessage({ kind: 'failed', text: 'the worker errored: ' + detail });
});

var jebolStoreRoot = null;

function jebolStoreRootDirectory() {
  if (!jebolStoreRoot) {
    jebolStoreRoot = navigator.storage.getDirectory();
  }
  return jebolStoreRoot;
}

function jebolStoreSegments(path) {
  return path.split('/').filter(function (segment) { return segment.length > 0; });
}

async function jebolStoreDirectoryFor(path, create) {
  var directory = await jebolStoreRootDirectory();
  for (const segment of jebolStoreSegments(path)) {
    directory = await directory.getDirectoryHandle(segment, { create: create });
  }
  return directory;
}

async function jebolStoreFileFor(path, create) {
  var segments = jebolStoreSegments(path);
  var name = segments.pop();
  var directory = await jebolStoreDirectoryFor(segments.join('/'), create);
  return directory.getFileHandle(name, { create: create });
}

async function jebolStoreEverything() {
  var found = [];
  async function walk(directory, path) {
    for await (const entry of directory.entries()) {
      var name = entry[0];
      var handle = entry[1];
      if (handle.kind === 'directory') {
        found.push({ path: path + name + '/', directory: true, octets: new Int8Array() });
        await walk(handle, path + name + '/');
      } else {
        var access = await handle.createSyncAccessHandle();
        var octets = new Int8Array(access.getSize());
        access.read(octets, { at: 0 });
        access.close();
        found.push({ path: path + name, directory: false, octets: octets });
      }
    }
  }
  try {
    await walk(await jebolStoreRootDirectory(), '/');
    return { failed: null, entries: found };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem), entries: [] };
  }
}

async function jebolStoreWrite(path, octets) {
  try {
    var file = await jebolStoreFileFor(path, true);
    var access = await file.createSyncAccessHandle();
    access.truncate(0);
    access.write(octets, { at: 0 });
    access.flush();
    access.close();
    return { failed: null };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem) };
  }
}

async function jebolStoreMakeDirectory(path) {
  try {
    await jebolStoreDirectoryFor(path, true);
    return { failed: null };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem) };
  }
}

async function jebolStoreRemove(path) {
  try {
    var segments = jebolStoreSegments(path);
    var name = segments.pop();
    var directory = await jebolStoreDirectoryFor(segments.join('/'), false);
    await directory.removeEntry(name, { recursive: true });
    return { failed: null };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem) };
  }
}

var jebolDigestNames = {
  'sha1': 'SHA-1', 'sha256': 'SHA-256', 'sha384': 'SHA-384', 'sha512': 'SHA-512'
};

async function jebolDigest(method, octets) {
  try {
    var taken = await crypto.subtle.digest(jebolDigestNames[method], octets);
    return { failed: null, octets: new Int8Array(taken) };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem), octets: new Int8Array() };
  }
}

async function jebolKeyedDigest(method, key, octets) {
  try {
    var imported = await crypto.subtle.importKey('raw', key,
        { name: 'HMAC', hash: { name: jebolDigestNames[method] } }, false, ['sign']);
    var signed = await crypto.subtle.sign('HMAC', imported, octets);
    return { failed: null, octets: new Int8Array(signed) };
  } catch (problem) {
    return { failed: String(problem && problem.message || problem), octets: new Int8Array() };
  }
}

var jebolStartedLoadingAt = performance.now();

TeaVM.wasmGC.load('jebol-web.wasm').then(function (teavm) {
  var loadedAt = performance.now();
  jebol = teavm.exports;
  try {
    teavm.exports.main([]);
  } catch (problem) {
    var why = problem && problem.stack ? problem.stack : String(problem);
    self.postMessage({ kind: 'failed', text: 'booting the interpreter threw: ' + why });
    return;
  }
  self.postMessage({
    kind: 'loaded',
    loadMs: Math.round(loadedAt - jebolStartedLoadingAt),
    mainMs: Math.round(performance.now() - loadedAt)
  });
}, function (why) {
  self.postMessage({ kind: 'failed', text: 'failed to load the interpreter: ' + why });
});
