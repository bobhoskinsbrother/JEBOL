Rebol [
	title: "NEAR and WHERE of errors, by how the failing call's last argument arrived"
	purpose: {Run by hand on ./r3-head and the jar; goal 1e's grid starts from what it finds.}
]

show: func [label failure] [
	print rejoin [
		label " -> " failure/id
		" | near: " cut failure/near " " mold type? failure/near
		" | where: " cut failure/where " " mold type? failure/where
	]
]

cut: func [x] [copy/part mold/flat x 60]

f: func [x [block!]] [x]
h: func [] [6]
q: func ['x [block!]] [x]
u: func ['x [integer!]] [x]
z: func [] [1 / 0]

; written
show "written: refused" try [f 6]
show "written: operator refused" try [1 + "a"]
show "written: refinement refused" try [append/only 6 7]
show "written: native raises" try [compress "a" 'brotli]
show "written: no-arg native" try [compress]
show "written: no-arg function" try [f]
show "written: unevaluated missing" try [u]

; from a paren
show "paren: native raises" try [compress "a" (quote brotli)]
show "paren: refused" try [f (6)]
show "paren: operator refused" try [1 + ("a")]
show "paren: unset at end of block" try [if false () 6]
show "paren: a paren DO evaluates" try [do (quote (1 / 0))]

; from a function, a get-word, unevaluated
show "function: refused" try [f h]
show "get-word: refused" try [f :h]
show "unevaluated: refused" try [q 6]

; already agreeing, and to stay so
show "inside a function" try [z]
show "inside repeat" try [repeat i 1 [1 / 0]]
show "inside foreach" try [foreach i [1] [1 / 0]]
show "inside reduce" try [reduce [1 / 0]]
show "inside parse" try [parse "a" [(1 / 0)]]
show "inside make object!" try [make object! [x: 1 / 0]]
show "either's condition" try [either 1 / 0 [1] [2]]
show "cause-error" try [cause-error 'user 'message "x"]
show "do make error!" try [do make error! "x"]
show "copy/part" try [copy/part "abc" "x"]
show "apply" try [apply :f [6]]
show "nested do" try [do [1 / 0]]
