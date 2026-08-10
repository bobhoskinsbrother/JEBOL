; Assertions from parse-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; parse-test.r3 / COLLECT/KEEP / block collect nested (known issues) #98
; why: R3 3.22.1 does not answer true to this
	--assert [[1] [2]]     == parse [1 2][collect some [collect keep integer!]]

; parse-test.r3 / COLLECT/KEEP / block collect nested (known issues) #99
; why: R3 3.22.1 does not answer true to this
	--assert [[1] a [2] a] == parse [1 2][collect some [collect keep integer! keep ('a)]]

; parse-test.r3 / COLLECT/KEEP / block collect nested (known issues) #100
; why: R3 3.22.1 does not answer true to this
	--assert [[1 a] [2 a]] == parse [1 2][collect some [collect [keep integer! keep ('a)]]]
