REBOL [Title: "Rough stand-ins for R3's three text-measuring commands, for a first look only"]
plain-text-of: func [gob /local t] [
	t: gob/text
	either block? t [ajoin collect [foreach v t [if string? v [keep v]]]] [any [t ""]]
]
size-text: func [gob] [as-pair 7 * length? plain-text-of gob 16]
caret-to-offset: func [gob element position] [
	as-pair 7 * either integer? position [position - 1] [0] 0
]
offset-to-caret: func [gob position] [reduce [gob/text 1 + to integer! position/x / 7]]
