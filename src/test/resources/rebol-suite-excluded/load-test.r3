; Assertions from load-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; load-test.r3 / Load issues/wishes / issue-2302 #31
; why: reads a file under units/, which was never vendored with the suite
		--assert all [
			error? e: try [load %units/files/invalid-decimal.r]
			e/near = "(line 4) 4line"
		]
