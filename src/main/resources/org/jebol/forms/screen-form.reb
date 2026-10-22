Rebol [
	Title: "JEBOL screen form"
]

ask-on-screen: func [
	masked [logic!]
	label [string!]
	/local typed answer finished window field show-typed screen
][
	typed: copy ""
	answer: none
	finished: false
	screen: system/view/screen-gob

	field: make gob! [offset: 16x38 size: 328x26 color: 255.255.255]
	window: make gob! [size: 360x100 color: 236.238.242]
	append window make gob! [offset: 16x12 size: 328x20 text: label]
	append window field
	append window make gob! [offset: 16x70 size: 328x20 text: "Enter to answer, Escape to decline"]
	window/offset: max 0x0 screen/size - window/size / 2

	show-typed: func [] [
		field/text: either masked [append/dup copy "" #"•" length? typed] [copy typed]
		show window
	]

	handle-events [
		name: 'jebol-screen-form
		priority: 1000
		handler: func [event] [
			switch event/type [
				key [
					case [
						event/key = #"^M" [answer: copy typed finished: true]
						event/key = #"^[" [finished: true]
						event/key = #"^H" [unless empty? typed [take/last typed] show-typed]
						all [event/key >= #" " event/key <> #"^~"] [append typed event/key show-typed]
					]
				]
				control [
					if event/key = 'escape [finished: true]
				]
				close [finished: true]
			]
			none
		]
	]

	append screen window
	show-typed
	until [
		wait [system/ports/event 0.05]
		finished
	]
	unhandle-events handled-events? 'jebol-screen-form
	remove find screen window
	show window
	answer
]
