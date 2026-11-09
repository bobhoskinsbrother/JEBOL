Rebol [
	title: "What a tcp port reports, one scenario per run"
	purpose: {Run by waiting.sh on ./r3-head against a local peer; WaitingOnAPortFromTheSourceTest asserts the answers.}
]

args: split trim form system/script/args #" "
scenario: to word! first args
address: rejoin [tcp://127.0.0.1: second args]

steps: [lookup [open event/port] connect [write event/port "hello"] wrote [read event/port]]
reads-until-closed: [lookup [open event/port] connect [read event/port] read [read event/port]]

answer: switch scenario [
	ends-where-awake-says-true [
		seen: copy []
		c: open address
		c/awake: func [event] [append seen event/type  switch event/type bind steps 'event event/type = 'wrote]
		reduce [port? wait [c 2] seen]
	]
	keeps-going-while-false [
		seen: copy []
		c: open address
		c/awake: func [event] [append seen event/type  switch event/type bind steps 'event false]
		reduce [port? wait [c 1] seen]
	]
	a-truthy-non-logic-does-not-end-it [
		seen: copy []
		c: open address
		c/awake: func [event] [append seen event/type  switch event/type bind steps 'event "yes"]
		reduce [port? wait [c 1] seen]
	]
	reported-once-each [
		seen: copy []
		c: open address
		c/awake: func [event] [append seen event/type  switch event/type bind steps 'event false]
		wait [c 1]
		wait [c 0.5]
		seen
	]
	a-settled-port-answers-none [
		c: open address
		c/awake: func [event] [switch event/type bind steps 'event false]
		wait [c 1]
		reduce [wait [c 0.2] wait [0.2 c] wait [0:00:00.2 c]]
	]
	no-awake-ends-at-once [
		c: open address
		c/awake: none
		port? wait [c 1]
	]
	not-connected-before-connect [
		c: open address
		written: try [write c "hello"]
		read-back: try [read c]
		reduce [written/id read-back/id]
	]
	bytes-add-to-the-data [
		c: open address
		c/awake: func [event] [switch event/type bind reads-until-closed 'event event/type = 'close]
		wait [c 2]
		to string! c/data
	]
	state-and-extra-after-open [
		c: open address
		reduce [true? c/state none? c/extra]
	]
	open-only-while-connected [
		c: open address
		before-connect: open? c
		c/awake: func [event] [switch event/type bind steps 'event  event/type = 'connect]
		wait [c 2]
		was-open: open? c
		close c
		reduce [before-connect was-open open? c true? c/state]
	]
	extra-is-the-scripts-own [
		c: open address
		c/extra: make object! [mine: "the script's own"]
		c/awake: func [event] [switch event/type bind steps 'event  event/type = 'read]
		wait [c 2]
		reduce [to string! c/data c/extra/mine]
	]
	something-else-in-state-is-not-open [
		c: make port! [scheme: 'tcp host: "127.0.0.1" port: 1]
		c/state: make object! [borrowed: "somebody else's"]
		open? c
	]
	a-close-when-nothing-more-arrives [
		seen: copy []
		c: open address
		c/awake: func [event] [append seen event/type  switch event/type bind reads-until-closed 'event event/type = 'close]
		reduce [port? wait [c 2] seen]
	]
]
print [scenario "->" mold/flat answer]
