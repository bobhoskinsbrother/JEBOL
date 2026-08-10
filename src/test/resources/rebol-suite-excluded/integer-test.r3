; Assertions from integer-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; integer-test.r3 / factorial / factorial #184
; why: factorial is not in this build of R3 at all
		--assert 1 == factorial 0

; integer-test.r3 / factorial / factorial #185
; why: factorial is not in this build of R3 at all
		--assert 1 == factorial 1

; integer-test.r3 / factorial / factorial #186
; why: factorial is not in this build of R3 at all
		--assert 2 == factorial 2

; integer-test.r3 / factorial / factorial #187
; why: factorial is not in this build of R3 at all
		--assert 6 == factorial 3

; integer-test.r3 / factorial / factorial #188
; why: factorial is not in this build of R3 at all
		--assert 2432902008176640000 == factorial 20

; integer-test.r3 / factorial / factorial #189
; why: factorial is not in this build of R3 at all
		--assert 7.25741561530799e306 = factorial 170
