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

print "--- open creates the file"
show [exists? %made.db]
db: s/open %made.db
show [exists? %made.db]
show [size? %made.db]
show [s/exec db "create table t(a)"]
show [size? %made.db]
show [s/eval db "insert into t values (1), (2)"]
show [s/close db]
show [size? %made.db]
show [copy/part read %made.db 16]

print "--- what was written is read back"
db: s/open %made.db
show [s/eval db "select a from t order by a"]
show [s/close db]

print "--- a second handle sees what the first wrote, before either closes"
one: s/open %shared.db
show [s/exec one "create table t(a); insert into t values ('first')"]
two: s/open %shared.db
show [s/eval two "select a from t"]
show [s/eval one "insert into t values ('second')"]
show [s/eval two "select a from t order by rowid"]
show [s/close one]
show [s/close two]

print "--- a transaction is not seen until it commits"
one: s/open %txn.db
show [s/exec one "create table t(a)"]
show [s/exec one "begin; insert into t values (1)"]
two: s/open %txn.db
show [s/eval two "select count(*) from t"]
show [s/exec one "commit"]
show [s/eval two "select count(*) from t"]
show [s/close one]
show [s/close two]

print "--- paths"
make-dir %sub/
change-dir %sub/
db: s/open %inner.db
show [s/exec db "create table t(a)"]
show [s/close db]
change-dir %..
show [exists? %sub/inner.db]
show [exists? %inner.db]
show [s/open %no/such/dir/x.db]
show [s/open %sub/]
db: s/open %sub/../made.db
show [s/eval db "select count(*) from t"]
show [s/close db]

print "--- what is not a database"
write %text.db "This is not a database, only some words in a file."
db: s/open %text.db
show [s/eval db "select 1"]
show [s/exec db "select * from sqlite_master"]
show [s/prepare db "select * from sqlite_master"]
show [s/close db]
write %empty.db #{}
db: s/open %empty.db
show [s/eval db "select count(*) from sqlite_master"]
show [s/exec db "create table t(a)"]
show [s/close db]
show [0 < size? %empty.db]

print "--- the database in memory never touches a file"
db: s/open to file! ":memory:"
show [s/exec db "create table t(a); insert into t values (1)"]
show [s/close db]
show [exists? to file! ":memory:"]
db: s/open %""
show [s/exec db "create table t(a); insert into t values (1)"]
show [s/eval db "select a from t"]
show [s/close db]
show [exists? %""]
