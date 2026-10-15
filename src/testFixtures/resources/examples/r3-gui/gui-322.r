    REBOL [Title: "Load the 2010 R3 GUI on 3.22"]
gui: load %gui-source.r
remove/part gui 5

close-trailing-set-words: func [block] [
	forall block [
		if all [
			find [object context] block/1
			block? block/2
			set-word? last block/2
		] [append block/2 none]
		if block? block/1 [close-trailing-set-words block/1]
	]
]

names-the-event-port?: func [path] [
	all [
		any [path? path set-path? path get-path? path]
		3 <= length? path
		path/1 = 'system path/2 = 'view path/3 = 'event-port
	]
]

event-port-written-as-a-word: func [path /local rest] [
	rest: skip to block! path 3
	either empty? rest [
		either set-path? path [to set-word! 'gui-event-port] [to word! 'gui-event-port]
	] [
		insert rest 'gui-event-port
		either set-path? path [to set-path! rest] [to path! rest]
	]
]

a-ports-locals-written-as-its-extra: func [path /local at] [
	if all [
		any [path? path set-path? path get-path? path]
		at: find path 'locals
	] [change at 'extra]
	path
]

move-the-event-port-out-of-system-view: func [block] [
	forall block [
		if names-the-event-port? block/1 [change/only block event-port-written-as-a-word block/1]
		a-ports-locals-written-as-its-extra block/1
		if block? block/1 [move-the-event-port-out-of-system-view block/1]
		if paren? block/1 [move-the-event-port-out-of-system-view block/1]
	]
]

a-word-given-to-set-as-a-get-word-is-set-as-a-plain-word: func [block] [
	forall block [
		if all [word? block/1 block/1 = 'set get-word? block/2] [
			insert next block [to word!]
		]
		if any [block? block/1 paren? block/1] [
			a-word-given-to-set-as-a-get-word-is-set-as-a-plain-word block/1
		]
	]
]

the-key-handler-also-takes-the-keys-that-type-nothing: func [block] [
	forall block [
		if all [set-word? block/1 block/1 = quote key-up:] [
			insert block [control: control-up:]
			block: skip block 2
		]
		if block? block/1 [the-key-handler-also-takes-the-keys-that-type-nothing block/1]
	]
]

the-show-queue-is-shown-one-gob-at-a-time: func [block] [
	forall block [
		if all [word? block/1 block/1 = 'show block/2 = 'list] [
			change/part block reduce ['foreach 'queued-gob block/2 [show queued-gob]] 2
		]
		if any [block? block/1 paren? block/1] [
			the-show-queue-is-shown-one-gob-at-a-time block/1
		]
	]
]

every-colours-fourth-number-is-read-as-its-transparency: func [block /local colour] [
	forall block [
		if all [tuple? block/1 4 = length? block/1] [
			colour: block/1
			colour/4: 255 - colour/4
			change block colour
		]
		if any [block? block/1 paren? block/1] [
			every-colours-fourth-number-is-read-as-its-transparency block/1
		]
	]
]

a-colour-scaled-as-2010-scaled-it: func [colour multiplier /local scaled] [
	scaled: colour * multiplier
	if 4 = length? colour [scaled/4: 255 - min 255 to integer! 255 - colour/4 * multiplier]
	scaled
]

span-colors-scales-transparency-as-2010-did: func [block] [
	forall block [
		if all [set-word? block/1 block/1 = quote span-colors:] [
			block/4: [
				out: make block! length? muls
				foreach v muls [append out a-colour-scaled-as-2010-scaled-it color v]
				out
			]
		]
		if block? block/1 [span-colors-scales-transparency-as-2010-did block/1]
	]
]

reset-the-faces-in: func [panel] [
	foreach face any [select panel 'faces []] [
		case [
			has-actor? face 'on-reset [do-style face 'on-reset none]
			find [check radio toggle] face/style [set-face face true? get-facet face 'orig-state]
		]
		if select face 'faces [reset-the-faces-in face]
	]
]

reset-everything-in-the-panel-of: func [face /local outer] [
	while [not select face 'names] [
		unless outer: parent-face? face [break]
		face: outer
	]
	reset-the-faces-in face
]

reset-with-no-name-resets-the-whole-form: func [block] [
	forall block [
		if all [
			set-word? block/1 block/1 = quote reset:
			block? block/2 string? first block/2
			block? block/3
		] [
			block/3: [either name [do-style get name 'on-reset none] [reset-everything-in-the-panel-of face]]
		]
		if block? block/1 [reset-with-no-name-resets-the-whole-form block/1]
	]
]

close-trailing-set-words gui
move-the-event-port-out-of-system-view gui
a-word-given-to-set-as-a-get-word-is-set-as-a-plain-word gui
the-key-handler-also-takes-the-keys-that-type-nothing gui
the-show-queue-is-shown-one-gob-at-a-time gui
every-colours-fourth-number-is-read-as-its-transparency gui
span-colors-scales-transparency-as-2010-did gui
reset-with-no-name-resets-the-whole-form gui
gui-event-port: none
do gui
