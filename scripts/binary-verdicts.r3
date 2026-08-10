Rebol [
    Title: "Which of Rebol's own assertions does Rebol's own binary fail?"
    Purpose: {
        Runs the exported suite steps through this binary, in the order
        the file wrote them, and prints the id of every assertion that
        does not answer true.

        The slicing is JEBOL's, exported by SuiteExportTest, so that both
        sides are answering the same question about the same expression.
        Steps arrive as hex because a test file's source is full of
        braces, carets and quotes, and hex has none of them.
    }
]

steps: load to file! first system/options/args

; One expression at a time. An assertion line may carry ordinary code
; after the assertion -- `--assert all [...] a: none` resets a -- so the
; first expression is the assertion and whatever follows still has to run.
run-first: func [source /local code answer][
    set/any 'code try [load source]
    if error? :code [return :code]
    ; LOAD of a single expression hands back the value rather than a
    ; block holding it, so `load "5"` is 5 and not [5].
    unless block? :code [code: append copy [] :code]
    if empty? code [return none]
    set/any 'answer try [do/next code (quote code)]
    ; Anything after the first expression is ordinary code. It runs for
    ; its effect and its own failure is not the assertion's failure.
    unless empty? code [try [do code]]
    either value? 'answer [:answer][none]
]

while [not tail? steps][
    either steps/1 = 'assert [
        answer: run-first to string! steps/2
        unless :answer = true [
            ; The source goes out with the id. Reading the group name and
            ; guessing which assertion in it failed gets the wrong one,
            ; which is how four passing assertions nearly got deleted.
            ; The id, then where it sits in its file, so that whatever
            ; takes it out again cuts in exactly the right place.
            print ["BINARY-FAILS" steps/4 steps/5 to string! steps/3]
        ]
        steps: skip steps 5
    ][
        run-first to string! steps/2
        steps: skip steps 2
    ]
]
print "DONE"
