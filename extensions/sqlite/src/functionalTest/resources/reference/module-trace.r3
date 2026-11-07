Rebol []
s: either all [block? system/options/args not empty? system/options/args] [
	import (to-rebol-file system/options/args/1)
][
	import 'sqlite
]
db: s/open to file! ":memory:"
s/exec db "create table t(a)"
print "--- 1: the statement"
s/trace db 1
s/eval db ["insert into t values (?)" 7]
s/eval db ["select a from t where a = ?" "text"]
s/exec db "select 1; select 2"
st: s/prepare db "select ?, ?"
s/step/with st [1.5 #(none)]
s/finalize st
print "--- 4: each row"
s/trace db 4
s/eval db "select 1 union all select 2"
print "--- 6: rows and timings"
s/trace db 2 + 4
s/eval db "select 1"
print "--- 0: nothing"
s/trace db 0
s/eval db "select 1"
print "--- 17: masked to 1"
s/trace db 17
s/eval db "select 17"
print "--- 8: the close"
s/trace db 8
s/close db
print "--- done"
