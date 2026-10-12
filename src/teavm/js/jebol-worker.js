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

TeaVM.wasmGC.load('jebol-web.wasm').then(function (teavm) {
  jebol = teavm.exports;
  try {
    teavm.exports.main([]);
  } catch (problem) {
    var why = problem && problem.stack ? problem.stack : String(problem);
    self.postMessage({ kind: 'failed', text: 'booting the interpreter threw: ' + why });
  }
}, function (why) {
  self.postMessage({ kind: 'failed', text: 'failed to load the interpreter: ' + why });
});
