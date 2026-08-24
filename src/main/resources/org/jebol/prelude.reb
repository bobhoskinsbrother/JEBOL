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

empty?: func [
    "Whether a series has nothing left from where it is."
    series
][
    ;; None answers true rather than refusing, which is the useful way
    ;; round for a guard: code asks this about a series it may not have
    ;; got back. TAIL? on its own refuses none, and so do FIRST, HEAD and
    ;; NEXT -- only this, INDEX? and LENGTH? forgive it.
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

;; system/standard holds the shapes that other functions build from.
;; Rebol keeps it in sysobj.reb and ENUM reads the enum object out of it.
;; MAKE-SCHEME puts each scheme it builds in system/schemes, and INPUT reads
;; system/ports/input. Both start empty, thus a scheme exists only once
;; something has registered it.
;; The console options, copied from sysobj.reb. Data rather than functions,
;; like system/standard below it.
;;
;; NO-COLOR and ANSI are what mezz-logger.reb reads on its fourth line, and
;; their absence stopped that file before it defined any of its five
;; functions. LOG is a map of verbosity per service, and SELECT on it
;; answering none is what makes a service fall back to the file's own
;; default.
append system/options reduce [
    ;; QUIET is read by every one of mezz-logger.reb's five functions before
    ;; they write anything, and by Rebol's start-up banner.
    to set-word! 'quiet     false
    to set-word! 'no-color  false
    to set-word! 'log       make map! [rebol: 1 http: 1 tls: 1 zip: 1 tar: 1]
    to set-word! 'ansi      make map! [
        reset:             "^[[0m"
        bold:              "^[[1m"
        italic:            "^[[3m"
        underline:         "^[[4m"
        invert:            "^[[7m"
        bold-off:          "^[[22m"
        italic-off:        "^[[23m"
        underline-off:     "^[[24m"
        invert-off:        "^[[27m"
        foreground:        "^[[39m"
        background:        "^[[49m"
        gray:              "^[[38;5;244m"
        black:             "^[[30m"
        red:               "^[[31m"
        green:             "^[[32m"
        yellow:            "^[[33m"
        blue:              "^[[34m"
        magenta:           "^[[35m"
        cyan:              "^[[36m"
        white:             "^[[37m"
        bright-red:        "^[[91m"
        bright-green:      "^[[92m"
        bright-yellow:     "^[[93m"
        bright-blue:       "^[[94m"
        bright-magenta:    "^[[95m"
        bright-cyan:       "^[[96m"
        bright-white:      "^[[97m"
        black-bg:          "^[[40m"
        red-bg:            "^[[41m"
        green-bg:          "^[[42m"
        yellow-bg:         "^[[43m"
        blue-bg:           "^[[44m"
        magenta-bg:        "^[[45m"
        cyan-bg:           "^[[46m"
        white-bg:          "^[[47m"
        bright-red-bg:     "^[[101m"
        bright-green-bg:   "^[[102m"
        bright-yellow-bg:  "^[[103m"
        bright-blue-bg:    "^[[104m"
        bright-magenta-bg: "^[[105m"
        bright-cyan-bg:    "^[[106m"
        bright-white-bg:   "^[[107m"
        error:             "^[[38;5;201m"
        banner:            "^[[30;107m"
    ]
]

append system reduce [
    to set-word! 'schemes  make object! []
    ;; The seven fields sysobj.reb declares, all none until a host fills one.
    ;; Not decoration: `Get_Event_Var` reads EVENT for a GUI event's port,
    ;; CALLBACK for a callback's and INPUT for a console event's, so a field
    ;; missing here is a field an event answers nothing for. All seven are none
    ;; in a stock console 3.22.1 too, which is why Rebol's own event test guards
    ;; its port case with `if system/ports/event [...]`.
    to set-word! 'ports    make object! [
        system: none
        event: none
        input: none
        output: none
        echo: none
        mail: none
        callback: none
    ]
]

;; system/view, the twentieth and last of sysobj.reb's top-level fields and
;; the one JEBOL never copied. view-funcs.reb reads SCREEN-GOB on its own
;; first line of INIT-VIEW-SYSTEM and walks METRICS with FOREACH straight
;; after, so its absence is why VIEW raises invalid-path rather than doing
;; anything.
;;
;; The six metrics start at 0x0 and never at none. sysobj.reb writes them as
;; one chained run of set-words ending in a single value, so all six take that
;; value, and a real 3.22.1 with no graphics host answers 0x0 for every one of
;; them. This was copied without the trailing value and read as none for as
;; long as it existed, which VIEW would have found the hard way: it centres a
;; window with `screen/size - window/size / 2` and offsets it by title-size,
;; and neither subtraction works on none.
append system reduce [
    to set-word! 'view
    make object! [
        screen-gob: none
        handler: none
        metrics: construct [
            screen-size:
            border-size:
            border-fixed:
            title-size:
            work-origin:
            work-size: 0x0
        ]
    ]
]

append system reduce [
    to set-word! 'standard
    make object! [
        ;; The shapes a scheme and a port are built from, copied from
        ;; sysobj.reb. Data rather than functions: MAKE-SCHEME and MAKE-PORT*
        ;; in sys-ports.reb are Rebol's own and are loaded, and both build
        ;; from these.
        ;; What STATS/PROFILE fills in and answers. The same object every
        ;; time, refreshed in place, because that is what the C does --
        ;; `stats = Get_System(SYS_STANDARD, STD_STATS); *ds = *stats;` -- and
        ;; DELTA-PROFILE depends on it: it copies this, runs a block, asks
        ;; again, and differences the copy against the object it still holds.
        ;;
        ;; Thirteen fields in sysobj.reb's order, because DELTA-PROFILE walks
        ;; them with FOREACH rather than naming them.
        stats: make object! [
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

        scheme: make object! [
            name: none          ;; word of console, file, http and so on
            title: none         ;; user-friendly title for the scheme
            spec: none          ;; custom spec for the scheme, if it needs one
            info: none          ;; prototype info object that QUERY answers
            actor: none         ;; the handler for this scheme's port actions
            awake: none         ;; the handler for this scheme's port events
        ]

        port: make object! [
            spec: none          ;; published specification of the port
            scheme: none        ;; scheme object used for this port
            parent: none        ;; port's parent, for a port inside a port
            actor: none         ;; port action handler
            awake: none         ;; port awake function
            state: none         ;; internal state values, private
            extra: none         ;; the host's own storage
            data: none          ;; data buffer, usually binary or block
        ]

        port-spec-head: make object! [
            title: none         ;; user-friendly title for the port
            scheme: none        ;; reference to the scheme that defines it
            ref: none           ;; reference path or url, for errors
        ]

        ;; The port specs each scheme extends, copied from sysobj.reb. Data
        ;; rather than functions, like everything else in system/standard.
        ;;
        ;; Absent ones do not degrade: a set-path cannot create a field, so a
        ;; protocol file that writes to one stops there. PORT-SPEC-NET alone
        ;; was four of Rebol's own files -- prot-http, prot-pop3, prot-smtp and
        ;; prot-daytime all open by extending it.
        port-spec-file: make port-spec-head [
            path: none
        ]

        port-spec-net: make port-spec-head [
            host: none
            port: 80
            path: none
            target: none
            query: none
            fragment: none
        ]

        port-spec-checksum: make port-spec-head [
            scheme: 'checksum
            method: none
        ]

        port-spec-crypt: make port-spec-head [
            scheme: 'crypt
            direction: 'encrypt
            algorithm: none
            init-vector: none
            key: none
        ]

        port-spec-midi: make port-spec-head [
            scheme: 'midi
            device-in: none
            device-out: none
        ]

        ;; What QUERY answers about a file, and what a scheme's own QUERY
        ;; builds from: `info: make system/standard/file-info [...]`, which is
        ;; how both prot-http.reb and prot-mysql.reb describe what they are
        ;; looking at. Seven fields, in sysobj.reb's order, and the comment on
        ;; DATE is Rebol's own: "same as `modified` (it is here just for
        ;; backwards compatibility)".
        file-info: construct [
            name:
            size:
            type:
            date:
            modified:
            accessed:
            created:
        ]

        ;; The shape a module header is built from. MAKE-MODULE* starts with
        ;; `construct/with :spec system/standard/header`, so a header block
        ;; from a file is filled into a copy of this and every field it did
        ;; not set is none. Without the prototype that ATTEMPT answers none
        ;; and the ASSERT/TYPE below it raises.
        ;;
        ;; Ten fields, in sysobj.reb's order, because a header is read by
        ;; walking WORDS-OF. VERSION starts at 0.0.0 and TITLE at "Untitled"
        ;; because MAKE-MODULE* asserts that the first is a tuple, and a
        ;; header that says neither still has to make a module.
        header: make object! [
            version: 0.0.0
            title: "Untitled"
            name: none
            type: none
            date: none
            file: none
            author: none
            needs: none
            options: none
            checksum: none
        ]

        enum: make object! [
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

        ;; The rest of sysobj.reb's standard object. Seventeen of its
        ;; twenty-nine fields were never copied across, and a set-path cannot
        ;; create a field, so every one of them stopped whatever wrote to it:
        ;; view-funcs.reb died on its eighteenth line writing FONT, and
        ;; sys-base.reb's DO* builds `make system/standard/script` on every
        ;; script it runs.
        ;;
        ;; Declared here rather than in Java because sysobj.reb is REBOL and
        ;; this is the file that speaks it. Nothing about a record of empty
        ;; slots needs the host language.

        codec: construct [
            name:
            type:
            title:
            suffixes:
            decode:
            encode:
            identify:
        ]

        ;; The template every error is built from. CODE and TYPE and ID carry
        ;; sysobj.reb's own defaults rather than none, because that is what a
        ;; MAKE over this has to start from.
        error: construct [
            code: 0
            type: 'user
            id: 'message
            arg1:
            arg2:
            arg3:
            near:
            where:
        ]

        ;; What DO* fills in for each script it runs, and what system/script
        ;; holds afterwards.
        script: construct [
            title:
            header:
            parent:
            path:
            args:
        ]

        port-spec-serial: make port-spec-head [
            path: none
            speed: 115200
            data-size: 8
            parity: none
            stop-bits: 1
            flow-control: none
        ]

        port-spec-audio: make port-spec-head [
            scheme: 'audio
            source: none
            channels: 2
            rate: 44100
            bits: 16
            sample-type: 1
            loop-count: 0
        ]

        net-info: construct [
            local-ip:
            local-port:
            remote-ip:
            remote-port:
        ]

        console-info: construct [
            buffer-cols:
            buffer-rows:
            window-cols:
            window-rows:
            length:
        ]

        vector-info: construct [
            signed:
            type:
            size:
            length:
            minimum:
            maximum:
            range:
            sum:
            mean:
            median:
            variance:
            sample-variance:
            population-deviation:
            sample-deviation:
        ]

        date-info: construct [
            year:
            month:
            day:
            time:
            date:
            zone:
            hour:
            minute:
            second:
            weekday:
            yearday:
            timezone:
            utc:
            julian:
        ]

        handle-info: construct [
            type:
        ]

        midi-info: construct [
            devices-in:
            devices-out:
        ]

        extension: construct [
            lib-base:
            lib-file:
            lib-boot:
            command:
            cmd-index:
            words:
        ]

        type-spec: construct [
            title:
            type:
        ]

        ;; The last four are none in sysobj.reb and something else fills each.
        ;; BINCODE is the C's, written by the base-code block at
        ;; u-bincode.c:178, so it stays none until the bincode dialect is
        ;; ported. UTYPE is the C's dispatch table for a datatype JEBOL has
        ;; not got. FONT and PARA are view-funcs.reb's, and a real 3.22.1
        ;; leaves both none unless the build included VID.
        bincode: none
        utype: none
        font: none
        para: none
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
