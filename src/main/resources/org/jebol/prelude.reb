REBOL [
    Title: "JEBOL prelude"
    Purpose: {
        The half of the standard library that is written in REBOL.

        A function belongs here when it can be said in REBOL. It belongs
        in Java only when it reaches something the language cannot:
        machine arithmetic, the reader, the evaluator itself.

        Nothing tells a caller which layer a function came from, so a
        function may move between them without any script noticing.
    }
]

; MAX and MIN are not here. They were, written as a comparison and a
; choice between the two arguments, and that is wrong for pairs: MIN of
; 1x2 and 2x1 is 1x1, which is neither argument. Choosing between two
; whole values cannot produce a third, so they moved to Java.
max: :maximum
min: :minimum

;; Moved here from Java, following how Rebol splits its own library:
;; two thirds of theirs is written in REBOL, and none of these needs
;; anything the language cannot say.

;; Needed by the prelude itself, which runs before Rebol's library. The
;; library's own EMPTY? -- TAIL? widened with object! and none! in
;; mezz-series.reb -- replaces this one as soon as that file loads, and is
;; what a script sees. Deleting this makes the prelude fail to load.
empty?: func [
    "Whether a series has nothing left from where it is."
    series
][
    either none? series [true] [tail? series]
]

does: func [
    "A function of no arguments, which is the shape a thunk wants."
    body [block!]
][
    func [] body
]

rejoin: func [
    "Reduces a block and runs the results together into one value."
    block [block!]
][
    ; No REDUCE here. AJOIN reduces what it is given, and doing it twice
    ; evaluates the results of the first pass: `rejoin [first colors]`
    ; gave the word RED and then tried to look RED up.
    if empty? block [return block]
    ajoin block
]

; NEGATIVE? and POSITIVE? are not here. They were, written as a
; comparison against zero, and R3 refuses to compare a pair with a
; number at all -- so the only way to ask the question of a pair is to
; ask it of each half, which needs the halves.

; MAP-EACH is not here, and the reason is the rule for which layer a
; function belongs in. It has to bind the caller's block to the word it
; walks with, and binding a block to a context is not something REBOL can
; say without BIND. So it is a native, and would move here the day BIND is.

; The -of family. REBOL's own base-defs.reb generates these by walking
; system/catalog/reflectors, one function per entry, and that file does
; not load yet. Written out here in the same shape, so the day it does
; load these are what it replaces rather than something it collides with.
spec-of:   func ["A function's declared interface." value] [reflect :value 'spec]
words-of:  func ["The words a value holds." value] [reflect :value 'words]
types-of:  func ["The datatypes a function accepts." value] [reflect :value 'types]
title-of:  func ["A function's title." value] [reflect :value 'title]

; These were natives and did not have to be. Each says in REBOL exactly
; what its Java version said, which is the test the layering rule sets:
; a function belongs here unless it needs something REBOL cannot say.
;
; Carrying them in Java bought nothing and cost something -- more surface
; to be wrong on, and a definition that Rebol's own library silently
; replaces the moment that file is borrowed.
to-block:   func ["A value as a block." value] [to block! :value]
to-decimal: func ["A value as a decimal." value] [to decimal! :value]
to-string:  func ["A value as a string." value] [to string! :value]
to-word:    func ["A value as a word." value] [to word! :value]

keys-of:    func ["The keys a map holds." value] [reflect :value 'words]
values-of:  func ["The values a value holds." value] [reflect :value 'values]
body-of:    func ["A function's body." value] [reflect :value 'body]

; JOIN keeps the datatype of what it joins onto, so a file joined with a
; string is a file. Joining onto something that is not a series has no
; datatype to keep, so that falls back to a string.
join: func [
    "Concatenates values."
    value "Base value"
    rest "Value or block of values"
][
    append either series? :value [copy value] [form :value] reduce :rest
]

; COLLECT and KEEP as functions, which is a different thing from the
; PARSE keywords of the same names. Rebol's own SPLIT is built on them.
;
; In REBOL rather than Java because they need nothing Java has: a block
; to gather into, a KEEP the body can see, and DO. They were unwritable
; here until user functions stopped demanding a refinement's argument
; from every caller -- which is the sort of thing that makes a language
; look like it needs a native when it does not.
collect: func [
    "Evaluates a block and answers what KEEP put aside while it ran."
    body [block!]
    ; KEEP is a local, not a word left lying in the system context. It
    ; has to be declared because the prelude only gives a slot to words
    ; assigned at its top level, and this one is assigned inside a body.
    /local gathered keep
][
    gathered: copy []
    keep: func [
        "Puts a value aside for the COLLECT around this one."
        value
        /only "Keep a block whole rather than spreading it"
    ][
        either all [block? :value not only]
            [insert tail gathered value]
            [insert/only tail gathered :value]
        :value
    ]
    do bind body 'keep
    gathered
]

; CLOSURE gives each call its own context, and that context outlives the
; call when something inside captured it. FUNC's frame does not.
;
; Here it is FUNC, because JEBOL's FUNC already behaves this way -- which
; makes CLOSURE right and FUNC wrong. R3 raises not-defined for
; `f: func [x] [does [x]] g: f 1 g` and JEBOL answers 1. Fixing that
; means giving FUNC a frame that is done with when the call returns, and
; is a change to the evaluator rather than to this file.
closure: :func


; The TO-X family: one function per datatype a value can be made of, each
; a second name for the same call to TO.
;
; Generated from the catalogue rather than written out forty-five times,
; so a new datatype cannot arrive with the family half updated, and so no
; one of them can quietly answer something TO does not.
;
; The datatypes left out have nothing to convert to -- END, UNSET and NONE
; hold a single value each -- or belong to the interpreter rather than to
; a script. JAVA-OBJECT! is left out as well, because giving it one would
; settle a question about the Java boundary that nothing has yet asked.
;
; The new name is bound before it is set. A word built with TO WORD! is
; unbound, and SET on an unbound word has nowhere to put the value; BIND
; /NEW places it beside a word already in the library.
use [name spelling] [
    foreach type system/catalog/datatypes [
        spelling: head remove back tail form type
        unless find [
            "end" "unset" "none" "native" "action" "rebcode" "op" "frame"
            "task" "handle" "struct" "library" "utype" "java-object"
        ] spelling [
            name: bind/new to word! rejoin ["to-" spelling] 'to
            set name make function! reduce [
                reduce [
                    rejoin ["Converts to " spelling "! value."]
                    'value
                ]
                reduce ['to type to get-word! 'value]
            ]
        ]
    ]
]

;; Ported from a real R3, whose own definitions were read out of the
;; binary with BODY-OF. Written plainly here where Rebol's use COMPOSE and
;; TO PAREN! to build the loop body at run time: the behaviour is what
;; matters and the plain form says what it is.

default: func [
    "Sets a word to a value if it has not got one yet."
    'word [word! set-word! lit-word!] "The word, or :var for a computed one"
    value "What to set it to"
][
    ;; Answers the default whether or not it was needed, which reads
    ;; oddly and is what a real R3 does.
    unless all [value? word  not none? get word] [set word :value]
    :value
]

has: func [
    "A function with local names and no arguments."
    vars [block!] "The names that are local to it"
    body [block!] "What it does"
][
    make function! reduce [head insert copy/deep vars /local  copy/deep body]
]

; The test is put into the loop body as a paren rather than run with DO.
; DO evaluates the caller's block where the caller wrote it, so the word
; the loop is setting is not the word the test can see, and every test
; fails on a word with no value. Building the body with the test inside it
; lets FOREACH bind the two together, which is what Rebol's own definitions
; do and why theirs look as they do.
all-of: func [
    "True when every value passes the test, none when one does not."
    'word [word! block!] "The name to set each time"
    data [series! any-object! map! none!] "What to walk"
    test [block!] "The test"
    /local failed
][
    if none? data [return none]
    failed: false
    foreach (word) data compose/deep [
        unless (to paren! test) [failed: true break]
    ]
    either failed [none] [true]
]

any-of: func [
    "The first value that passes the test, or none if none does."
    'word [word! block!] "The name to set each time"
    data [series! any-object! map! none!] "What to walk"
    test [block!] "The test"
    /local found
][
    if none? data [return none]
    found: none
    foreach (word) data compose/deep [
        if (to paren! test) [found: (to get-word! word) break]
    ]
    found
]

wrap: func [
    "Evaluates a block with every set-word in it made local to the block."
    body [block!] "What to evaluate"
][
    ;; Rebol's own definition. The fresh object is what the set-words are
    ;; bound into, so `wrap [x: 2]` leaves any x outside alone.
    do bind/copy/set body make object! 0
]

funco: func [
    "Builds a function without copying its spec or body."
    spec [block!] "Its interface"
    body [block!] "What it does"
][
    ;; FUNC copies both so a caller cannot change a function from under
    ;; itself. This one does not, which is why Rebol keeps it for booting
    ;; and why nothing else should reach for it.
    make function! reduce [spec body]
]

map: func [
    "A map made from a block of keys and values."
    val "What to make it from"
][
    make map! :val
]

cause-error: func [
    "Raises an error of the given kind, as though the interpreter had."
    err-type [word!] "Its category"
    err-id [word!] "Which error"
    args "One argument, or a block of up to three"
][
    args: compose [(:args)]
    do make error! [
        type: err-type
        id: err-id
        arg1: first args
        arg2: second args
        arg3: third args
    ]
]

script?: func [
    "The start of a script header in some source, or none if there is none."
    source [binary! string!]
][
    find-script either string? source [to binary! source] [source]
]

;; The file-path family, ported from a real R3. Each is a copy: none of
;; them changes the path the caller holds.

dirize: func [
    "A copy of the path that names a directory, with a slash at its end."
    path [file! string! url!]
][
    path: copy path
    if slash <> last path [append path slash]
    path
]

undirize: func [
    "A copy of the path with any slash at its end taken off."
    path [file! string! url!]
][
    path: copy path
    if #"/" = last path [clear back tail path]
    path
]

suffix?: func [
    "The suffix of a file name, or none if it has none."
    path [file! url! string!]
][
    ;; A dot in a directory name is not a suffix, so the dot has to be
    ;; after the last slash. `%a.b/c` has no suffix.
    ;; TRUE? is not wanted here: the answer is the suffix itself, or
    ;; none. FIND/LAST gives the path from the dot onward, thus a slash
    ;; after that dot means the dot was in a directory name.
    all [
        path: find/last path #"."
        not find path #"/"
        to file! copy path
    ]
]

funct: func [
    "Builds a function in which every set-word in the body is a local."
    spec [block!] "Its interface"
    body [block!] "What it does"
    /extern words [block!] "Names that must not be made local"
][
    ;; Rebol's own definition. The set-words are collected from the body
    ;; and added after /local, so a function written this way cannot
    ;; change a word outside itself by accident.
    spec: copy/deep spec
    unless find spec /local [append spec [/local]]
    body: copy/deep body
    insert find/tail spec /local collect-words/deep/set/ignore body
        either extern [append copy spec words] [spec]
    make function! reduce [spec body]
]

clos: func [
    "Defines a closure: a function whose names outlive the call."
    spec [block!] "Its interface"
    body [block!] "What it does"
][
    ;; Rebol writes this as `make closure! copy/deep reduce [spec body]`.
    ;; JEBOL has no closure! to make, and its FUNC already keeps a frame
    ;; that outlives the call, so FUNC is what a closure means here. The
    ;; note beside CLOSURE above says why that is FUNC being wrong rather
    ;; than this being right.
    make function! copy/deep reduce [spec body]
]

split-path: func [
    "The directory and the name at the end of a path, as two values."
    target [file! url! string!] "The path to split"
    /local names-a-directory trimmed slash-at
][
    ;; Rebol's own definition, in base-files.reb, is one PARSE with two
    ;; rules. The first rule is the one that is easy to miss: a path made
    ;; only of slashes and dots is a directory with no name after it, thus
    ;; %/ splits into [%/ %""] and %../ into [%../ %""].
    if find ["/" "." ".." "./" "../"] to string! target [
        return reduce [dirize copy target to file! ""]
    ]
    names-a-directory: #"/" = last target
    trimmed: either names-a-directory [copy/part target back tail target] [copy target]
    slash-at: find/last trimmed #"/"
    either slash-at [
        reduce [
            to file! copy/part trimmed next slash-at
            to file! either names-a-directory
                [join copy next slash-at "/"]
                [copy next slash-at]
        ]
    ] [
        reduce [
            %./
            to file! either names-a-directory [join trimmed "/"] [trimmed]
        ]
    ]
]


clean-path: func [
    "A path with the double slashes, the dots and the double dots worked out."
    file [file! url! string!] "The path to clean"
    /only "Leave a relative path relative"
    /dir "Put a slash at the end if there is not one"
    /local text absolute kept part
][
    ;; Rebol writes this as one PARSE over the path read backwards, with
    ;; two counters. Written here as a walk over the parts, because the
    ;; parts are what the rules are about and reading backwards only makes
    ;; them harder to see.
    text: to string! file
    absolute: #"/" = first text
    unless any [only absolute] [
        text: join to string! what-dir text
        absolute: #"/" = first text
    ]
    kept: copy []
    foreach part split text "/" [
        case [
            any [part = "" part = "."] []
            ;; A double dot with nothing above it is dropped and not
            ;; kept. A path cannot climb above where it starts, thus
            ;; %../a cleans to %a.
            part = ".." [unless empty? kept [take/last kept]]
            true [append kept part]
        ]
    ]
    text: copy either absolute ["/"] [""]
    foreach part kept [append append text part "/"]
    unless any [dir empty? kept] [remove back tail text]
    to file! text
]

;; SYSTEM/SCHEMES, SYSTEM/PORTS and SYSTEM/VIEW are all declared in
;; sysobj.reb and the boot step before this makes them from it, so the only
;; thing left here is the one place JEBOL differs. Rebol starts SCHEMES as a
;; block and INIT-SCHEMES in sys-ports.reb turns it into an object once the
;; host has registered what it can serve; JEBOL registers its schemes from
;; Java instead, so the object has to exist before the first MAKE-SCHEME
;; rather than after.
system/schemes: make object! []
;; sysobj.reb declares SYSTEM/STANDARD and the boot step before this makes
;; the object from that file, so the shapes a scheme, a port, a header and
;; the rest are built from are Rebol's own text rather than a copy of it.
;;
;; Two of the declared fields are deliberately left empty there for something
;; else to fill, and this is what fills them. ENUM is marked "is defined
;; later in %mezz-func.r file" and carries two functions, which is why it
;; cannot be data in a declaration. STATS is what STATS/PROFILE refreshes in
;; place, and DELTA-PROFILE copies it, runs a block, asks again and
;; differences the two, so its thirteen fields start as numbers to subtract.

system/standard/stats: make object! [
    timer: 0:0:0
    evals: 0
    eval-natives: 0
    eval-functions: 0
    series-made: 0
    series-freed: 0
    series-expanded: 0
    series-bytes: 0
    series-recycled: 0
    made-blocks: 0
    made-objects: 0
    recycles: 0
    collisions: 0
]


system/standard/enum: make object! [
    title*: none

    assert: func [
        "Fails unless the value is one of the enumeration's."
        value [integer!]
    ][
        unless find values-of self value [
            cause-error 'script 'invalid-value-for reduce [value title*]
        ]
        true
    ]

    name: func [
        "The name of a value in the enumeration, or none."
        value [integer!]
        /local pos
    ][
        all [
            pos: find values-of self value
            pick words-of self index? pos
        ]
    ]
]

enum: func [
    "An enumeration object built from names and values."
    spec [block!] "The names, each with a value or without one"
    title [string! word!] "What the enumeration is called"
    /local next-value built at name
][
    ;; Rebol writes this as one PARSE that changes the block as it walks.
    ;; Written here as a walk, because the rule is simple: a name with no
    ;; value takes the one after the last, counting from zero.
    next-value: 0
    built: copy []
    at: spec
    while [not tail? at] [
        name: first at
        unless any-word? name [
            cause-error 'script 'invalid-data reduce [at]
        ]
        at: next at
        if all [not tail? at  not any-word? first at] [
            next-value: to integer! first at
            at: next at
        ]
        repend built [to set-word! to word! name next-value]
        next-value: next-value + 1
    ]
    built: make system/standard/enum built
    built/title*: title
    built
]
