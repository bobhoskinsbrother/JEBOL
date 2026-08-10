; Assertions from compare-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; compare-test.r3 / prefix equal same datatype / prefix-equal-same-datatype-25 #184
; why: R3 3.22.1 does not answer true to this
	--test-- "prefix-equal-same-datatype-25" --red-- --assert not equal? #"z" #"Z"

; compare-test.r3 / prefix-greater-same-datatype / prefix-greater-same-datatype-9 #260
; why: R3 3.22.1 does not answer true to this
	--test-- "prefix-greater-same-datatype-9"	--assert     greater? "è" "f"
