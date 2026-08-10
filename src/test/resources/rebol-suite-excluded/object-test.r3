; Assertions from object-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; object-test.r3 / EXTEND object / any-word as an object's key #23
; why: R3 3.22.1 does not answer true to this
		--assert [a: 10 b: 20 c: 30 d: 40] == body-of obj

; object-test.r3 / EXTEND object / bind to error #49
; why: R3 3.22.1 does not answer true to this
		--assert all [error? e: try [bind 'id err] e/id = 'expect-arg]
