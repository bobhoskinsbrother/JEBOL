Rebol [Title: "Where a line mark goes when its value moves"]

b: [a
b]
show: func [label [string!] answer] [
	print [label "|" replace/all either block? :answer [mold :answer] [mold reduce [:answer]] newline "\n"]
]
probes: [
	"a variable"                [x: second b reduce [x]]
	"second"                    [reduce [second b]]
	"pick"                      [reduce [pick b 2]]
	"a path"                    [reduce [b/2]]
	"last"                      [reduce [last b]]
	"first next"                [reduce [first next b]]
	"select"                    [reduce [select [k
v] 'k]]
	"foreach"                   [collect [foreach v b [keep v]]]
	"append"                    [x: second b append copy [] x]
	"append/only"               [x: second b append/only copy [] x]
	"insert"                    [x: second b head insert copy [] x]
	"insert/dup"                [x: second b head insert/dup copy [] x 2]
	"append a block"            [append copy [p] b]
	"compose"                   [x: second b compose [(x)]]
	"compose second"            [compose [(second b)]]
	"a variable copied"         [x: second b y: x reduce [y]]
	"an argument"               [f: func [a] [reduce [a]] f second b]
	"do"                        [reduce [do [second b]]]
	"set"                       [set 'z second b reduce [z]]
	"get"                       [x: second b reduce [get 'x]]
	"a literal"                 [reduce [
1]]
	"a sum"                     [reduce [
1 + 2]]
	"a literal set"             [w:
1 reduce [w]]
	"copy/deep"                 [reduce [copy/deep second [a
[c]]]]
	"copy a word"               [reduce [copy second b]]
	"to word!"                  [x: second b reduce [to word! x]]
	"to lit-word!"              [x: second b reduce [to lit-word! x]]
	"quote"                     [x: second b reduce [quote x]]
	"change"                    [x: second b head change copy [p q] x]
	"change over a mark"        [head change next copy [p
q] 'z]
	"poke"                      [x: second b c: copy [p q] poke c 1 x c]
	"poke over a mark"          [c: copy [p
q] poke c 2 'z c]
	"a set-path"                [x: second b o: make object! [m: none] o/m: x reduce [o/m]]
	"repend"                    [x: second b repend copy [] [x]]
	"make block!"               [x: second b make block! reduce [x]]
	"copy/part"                 [copy/part next b 1]
	"map-each"                  [map-each v b [v]]
	"if"                        [x: second b reduce [if true [x]]]
	"either"                    [reduce [either true [second b] [0]]]
	"a function's result"       [x: second b g: does [x] reduce [g]]
	"a string"                  [reduce [second ["s"
"t"]]]
	"an integer"                [reduce [second [1
2]]]
	"a block"                   [reduce [second [[1]
[2]]]]
	"a none"                    [reduce [second [#(none)
#(none)]]]
	"a logic"                   [reduce [second [#(true)
#(true)]]]
	"a map"                     [reduce [second reduce [1
make map! [k 1]]]]
	"a bitset"                  [reduce [second reduce [1
charset "a"]]]
	"same map"                  [m: make map! [k 1] c: reduce [1 m] new-line next c true reduce [same? m second c]]
	"an object field"           [x: second b o2: make object! [w: x] reduce [o2/w]]
	"new-line off"              [x: second b reduce [new-line/all reduce [x] false]]
	"new-line on, elsewhere"    [x: first b c: reduce [x] new-line c true reduce [b c]]
	"new-line off, elsewhere"   [c: copy b new-line next c false reduce [b c]]
	"find"                      [reduce [first find b 'b]]
	"reverse"                   [reverse copy b]
	"sort"                      [sort copy [c
b a]]
	"take"                      [reduce [take next copy b]]
	"remove"                    [c: copy [p
q r] remove c c]
	"unique"                    [unique [a
a b]]
	"to block!"                 [x: second b to block! x]
	"reduce/only"               [x: second b reduce/only [x] []]
	"load"                      [load "a^/b"]
	"a parsed copy"             [parse b [skip copy got skip] got]
	"a parsed set"              [parse b [skip set got skip] reduce [got]]
	"mold/only"                 [x: second b mold/only reduce [x 'c]]
	"a block in a block"        [x: second b reduce [reduce [x]]]
	"a paren"                   [x: second b to paren! reduce [x]]
	"a lit-word in code"        [reduce ['a
'b]]
	"a word fetched twice"      [x: second b reduce [x x]]
	"the tail"                  [[a
]]
	"deline/lines"              [deline/lines "one^/two"]
	"split-lines"               [split-lines "one^/two"]
	"a line of deline/lines"    [reduce [first deline/lines "one^/two"]]
	"words-of a function"       [words-of func [
a
b] [a]]
	"values-of an object"       [values-of make object! [a:
1 b:
2]]
	"a word of the catalogue"   [reduce [pick system/catalog/event-keys 6]]
	"the catalogue's own marks" [copy/part system/catalog/event-types 2]
	"spec-of a function"        [spec-of func [
"Doc."
a] [a]]
]
foreach [label code] probes [
	answer: try code
	either error? :answer [print [label "|" "error" answer/id]] [show label :answer]
]
