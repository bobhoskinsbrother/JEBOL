var jebolSqlite = null;

function jebolSqliteStart() {
  if (jebolSqlite) {
    return Promise.resolve(true);
  }
  importScripts('sqlite3.js');
  return sqlite3InitModule({ print: function () {}, printErr: function () {} }).then(function (sqlite3) {
    jebolSqlite = sqlite3;
    return true;
  });
}

function jebolSqliteExports() {
  return jebolSqlite.wasm.exports;
}

function jebolSqliteCall(name, a, b, c, d, e, f) {
  var answer = jebolSqliteExports()[name](a, b, c, d, e, f);
  return typeof answer === 'bigint' ? Number(answer) : answer;
}

function jebolSqliteCallWithBigInt(name, a, b, big) {
  return jebolSqliteExports()[name](a, b, BigInt(big));
}

function jebolSqliteCallForBigInt(name, a, b) {
  return String(jebolSqliteExports()[name](a, b));
}

function jebolSqliteCallForPointer(name, a, b) {
  return jebolSqliteExports()[name](a, b) >>> 0;
}

function jebolSqliteDeserialize(database, schema, image, length, flags) {
  return jebolSqliteExports().sqlite3_deserialize(database, schema, image, BigInt(length), BigInt(length), flags);
}

function jebolSqliteWrite(octets) {
  var exports = jebolSqliteExports();
  var pointer = exports.sqlite3_malloc(octets.length + 1) >>> 0;
  var heap = jebolSqlite.wasm.heap8u();
  heap.set(new Uint8Array(octets.buffer, octets.byteOffset, octets.length), pointer);
  heap[pointer + octets.length] = 0;
  return pointer;
}

function jebolSqliteRead(pointer, length) {
  return new Int8Array(jebolSqlite.wasm.heap8u().slice(pointer, pointer + length).buffer);
}

function jebolSqliteReadInt(pointer) {
  return jebolSqlite.wasm.peek32(pointer) >>> 0;
}

function jebolSqliteReadLong(pointer) {
  return Number(jebolSqlite.wasm.peek64(pointer));
}

function jebolSqliteReadText(pointer) {
  return pointer ? jebolSqlite.wasm.cstrToJs(pointer) : null;
}
