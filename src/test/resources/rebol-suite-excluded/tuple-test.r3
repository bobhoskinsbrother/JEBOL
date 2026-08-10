; Assertions from tuple-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; tuple-test.r3 / as-color / as-color with integers #77
; why: as-color is not in this build of R3 at all
		--assert 1.2.3   == as-color  1 2 3

; tuple-test.r3 / as-color / as-color with integers #78
; why: as-color is not in this build of R3 at all
		--assert 0.2.255 == as-color -1 2 300

; tuple-test.r3 / as-color / as-color with decimals #79
; why: as-color is not in this build of R3 at all
		--assert 1.2.3   == as-color  1.0 2.4 2.7

; tuple-test.r3 / as-color / as-color with decimals #80
; why: as-color is not in this build of R3 at all
		--assert 0.3.255 == as-color -1.0 2.5 300.0

; tuple-test.r3 / as-color / as-color with percents #81
; why: as-color is not in this build of R3 at all
		--assert 0.128.255 == as-color 0 50% 100%
