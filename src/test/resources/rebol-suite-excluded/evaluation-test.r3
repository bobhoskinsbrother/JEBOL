; Assertions from evaluation-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; evaluation-test.r3 / do script / script returning UNSET value #40
; why: reads a file under units/, which was never vendored with the suite
		--assert unset? do %units/files/unset.r3

; evaluation-test.r3 / do script / script with quit #42
; why: reads a file under units/, which was never vendored with the suite
		--assert unset? do %units/files/quit.r3

; evaluation-test.r3 / do script / script with quit #43
; why: reads a file under units/, which was never vendored with the suite
		--assert 42 = do %units/files/quit-return.r3
