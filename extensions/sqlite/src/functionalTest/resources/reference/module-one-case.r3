Rebol []
args: any [system/options/args []]
s: either 2 = length? args [import (to-rebol-file first args)] [import 'sqlite]
answer-of: func [r [any-type!]] [
	case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		true [mold/flat :r]
	]
]
cases: [
	[s/exec db "select 1"]
	[s/eval db "select 1"]
	[s/eval db st]
	[s/prepare db "select 1"]
	[s/last-insert-id db]
	[s/trace db 3]
	[s/step st]
	[s/columns st]
	[s/reset st]
	[s/finalize st]
	[s/eval db "select x''"]
	[s/eval db "select x'', 1"]
	[s/eval db "select 1, x''"]
	[s/eval db "select 'abc', x''"]
	[s/eval db "select x'01', x''"]
	[s/step s/prepare db "select x''"]
	[s/eval db "select zeroblob(0)"]
]
either 2 = length? args [index: to integer! second args] [index: to integer! first args]
if index = 0 [print length? cases quit]
code: pick cases index
prin [mold/flat code "=> "]
db: s/open to file! ":memory:"
st: s/prepare db "select 1"
if index <= 10 [s/close db]
set/any 'r try code
print answer-of :r
