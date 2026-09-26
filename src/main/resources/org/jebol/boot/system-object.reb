;; Rebol declares the system object in sysobj.reb, so that file is what the
;; object is made from. The interpreter had written the same field names out
;; by hand, in Java and again in the prelude, and the two copies had drifted.
;;
;; What the interpreter knows and the declaration cannot is carried over from
;; the object it reported before this ran: which datatypes exist, which
;; functions it carries, which ciphers and checksums it can really perform,
;; and where this machine keeps its directories.

reported: system

;; THE-DECLARATION is the file's body, already read and bound to a context of
;; its own below the library. It needs one: the declaration writes set-words
;; that the library also uses as function names -- DATE, TYPE, SIZE, TEXT,
;; ERROR -- and binding it to the library itself overwrites every one of them.
system: make object! the-declaration

system/product:  reported/product
system/platform: reported/platform
system/version:  reported/version
system/license:  reported/license
system/codecs:   reported/codecs

;; The declaration offers fourteen compiled extensions -- sqlite, webp,
;; blend2d and the rest -- as addresses of shared libraries to download.
;; Nothing here can load one, and offering an address that sends IMPORT after
;; a .dylib this build cannot open is worse than offering nothing, so the
;; module table stays the one this build fills with what it really carries.
system/modules: reported/modules

foreach field [
    datatypes actions natives errors handles structs
    checksums ciphers compressions elliptic-curves filters file-types
][
    set in system/catalog field get in reported/catalog field
]

foreach field [
    boot path home data modules flags script args
    do-arg import debug secure version boot-level result-types
][
    set in system/options field get in reported/options field
]

;; USER is not here: the user context is published into whichever system
;; object is current, and that is done again once this one exists.
foreach field [lib sys root][
    set in system/contexts field get in reported/contexts field
]

;; The state object is the one the interpreter writes into while it runs --
;; the last error, the last result, what is waiting -- so that object is kept
;; and the declaration supplies only the sentence it carries about itself.
the-note: system/state/note
system/state: reported/state
system/state/note: the-note


