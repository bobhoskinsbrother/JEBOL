; Assertions from pair-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; pair-test.r3 / distance / distance basic #126
; why: distance is not in this build of R3 at all
	    --assert 5.0 = distance 0x0 3x4        ; 3-4-5 triangle

; pair-test.r3 / distance / distance same point #127
; why: distance is not in this build of R3 at all
	    --assert 0.0 = distance 5x5 5x5

; pair-test.r3 / distance / distance negative coords #128
; why: distance is not in this build of R3 at all
	    --assert 5.0 = distance -3x0 0x4       ; same triangle, negative x

; pair-test.r3 / distance / distance symmetry #129
; why: distance is not in this build of R3 at all
	    --assert (distance 1x2 4x6) = (distance 4x6 1x2)

; pair-test.r3 / distance / distance/taxicab basic #130
; why: distance is not in this build of R3 at all
	    --assert 7.0 = distance/taxicab 0x0 3x4

; pair-test.r3 / distance / distance/taxicab same point #131
; why: distance is not in this build of R3 at all
	    --assert 0.0 = distance/taxicab 5x5 5x5

; pair-test.r3 / distance / distance/taxicab negative coords #132
; why: distance is not in this build of R3 at all
	    --assert 7.0 = distance/taxicab -3x0 0x4

; pair-test.r3 / distance / distance/taxicab symmetry #133
; why: distance is not in this build of R3 at all
	    --assert (distance/taxicab 1x2 4x6) = (distance/taxicab 4x6 1x2)

; pair-test.r3 / distance / distance/taxicab axis aligned #134
; why: distance is not in this build of R3 at all
	    --assert 5.0 = distance/taxicab 0x0 5x0  ; horizontal — same as Euclidean
