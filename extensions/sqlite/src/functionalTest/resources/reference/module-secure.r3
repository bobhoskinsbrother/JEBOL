Rebol []
s: either all [block? system/options/args not empty? system/options/args] [
	import (to-rebol-file system/options/args/1)
][
	import 'sqlite
]
answer-of: func [r [any-type!] /local text] [
	text: case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		true [mold/flat :r]
	]
	replace/all text mold what-dir "%./"
]
show: func [code [block!] /local r] [
	set/any 'r try code
	print [mold/flat code "=>" answer-of :r]
]
db: s/open %allowed.db
s/exec db "create table t(a); insert into t values (1)"
s/close db
secure [file throw]
show [read %allowed.db]
show [db: s/open %allowed.db]
show [s/eval db "select a from t"]
show [s/eval db "insert into t values (2)"]
show [s/close db]
show [db: s/open %denied.db]
show [s/exec db "create table t(a)"]
show [s/close db]
