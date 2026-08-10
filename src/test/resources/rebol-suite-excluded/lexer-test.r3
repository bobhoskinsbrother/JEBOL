; Assertions from lexer-test.r3 that this build of R3 3.22.1 does not
; pass either. They are held here rather than deleted, and they are
; not run: an assertion the reference implementation fails says
; nothing about JEBOL, and counting it as a gap overstates the work.
;
; Each was checked against the binary one expression at a time by
; scripts/binary-verdicts.r3, over sources taken verbatim from the
; file rather than re-rendered.

; lexer-test.r3 / BINARY / binary! with comments inside #418
; why: R3 3.22.1 does not answer true to this
		--assert error? transcode/one/error "#{0}"

; lexer-test.r3 / Special tests / NULLs inside loaded string #452
; why: reads a file under units/, which was never vendored with the suite
		--assert try/with [
		;- using CALL as it could be reproduced only when the internal buffer is being extended durring load
			data: make string! 40000
			insert/dup data "ABCD" 10000

			dir: clean-path %units/files/
			save dir/tmp.data reduce [1 data]
			exe: system/options/boot
			;@@ CALL seems not to work same on all OSes :-(
			either system/platform = 'Windows [
				call/wait/output rejoin [to-local-file exe { -s } to-local-file dir/bug-load-null.r3] out
			][	call/wait/output reduce [exe "-s" dir/bug-load-null.r3] out ]

			;probe out
			parse out [thru "Test OK" to end]
		][
			probe system/state/last-error
			false
		]
		error? try [ delete dir/tmp.data ]
