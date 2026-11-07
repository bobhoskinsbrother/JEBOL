Rebol []
s: either all [block? system/options/args not empty? system/options/args] [
	import (to-rebol-file system/options/args/1)
][
	import 'sqlite
]
shape-of: func [text [string!] /local out] [
	out: copy text
	parse out [any [remove ["<" thru ">"] insert "<pointer>" | skip]]
	out
]
library-shape-of: func [text [string!] /local out] [
	out: copy text
	parse out [any [
		  ["SQLite-version: " | "SQLite-memory:  " | "SQLite-mem-top: "] remove to newline insert "?"
		| skip
	]]
	out
]
answer-of: func [r [any-type!]] [
	case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		true [mold/flat :r]
	]
]
show: func [code [block!] /local r] [
	set/any 'r try code
	print [mold/flat code "=>" answer-of :r]
]
memory: to file! ":memory:"
x: 41 o: object [y: "why"]

print "--- the module"
spec: spec-of s
show [reduce [spec/version spec/title spec/name spec/type spec/needs spec/author spec/License spec/Url spec/Exports]]
show [words-of s]
show [find words-of system/contexts/lib 'last-insert-id]
show [same? s system/modules/sqlite]
print ["info:" mold library-shape-of s/info]

print "--- open and exec"
db: s/open memory
show [type? :db]
show [mold :db]
print ["info/of db:" mold shape-of s/info/of db]
show [s/exec db ""]
show [s/exec db "   "]
show [s/exec db "create table t(a, b); insert into t values (1, 'one'), (2, 'two');"]
show [s/exec db "select * from t; select count(*) as n from t"]
show [s/exec db "select null as nothing"]
show [s/exec db "select null as nil, 1.5 as half, x'00ff' as bin, '' as empty"]
show [s/exec db "nonsense"]
show [s/exec db "select * from missing"]
show [s/exec db "create table nn(a not null); insert into nn values (null)"]
show [s/exec db "select 1; nonsense; select 2"]
show [s/exec db next "Xselect 7 as seven"]

print "--- values"
show [s/eval db "select 1, -1, 0, 9223372036854775807, -9223372036854775808"]
show [s/eval db "select 1.5, -0.25, 3.0, 1e300"]
show [s/eval db "select 'text', '', 'Océane', 'ěšč', '😀'"]
show [s/eval db "select null, x'00ff', x'01'"]
show [length? first s/eval db "select zeroblob(100000)"]
show [s/eval db "select length(zeroblob(100000))"]
show [s/eval db "select typeof(9223372036854775807 + 1)"]

print "--- eval"
show [s/eval db "select * from t order by a"]
show [s/eval db "select * from t where a > 100"]
show [s/eval db "insert into t values (3, 'three')"]
show [s/eval db "update t set b = upper(b)"]
show [s/eval db "select a from t order by a; select 99"]
show [s/eval db next "Xselect 7"]
show [s/eval db reduce [next "Xselect 8"]]
show [s/eval db "nonsense"]
show [s/eval db ""]
show [s/eval db []]
show [s/eval db [1]]
show [s/eval db ["select ?" 5]]
show [s/eval db ["select ?, ?" 5]]
show [s/eval db ["select ?" 5 6]]
show [s/eval db ["select 1"]]
sql: "select count(*) from t"
show [s/eval db [sql]]
show [s/eval db [:sql]]
show [s/eval db ["select ?" next "xA_di"]]
show [s/eval db reduce ["select ?" next "xA_di"]]
show [s/eval db reduce ["select ?, ?" next "xA_di" next "ěšč"]]
show [s/eval db reduce ["select ?" :print]]
show [s/eval db reduce ["select ?" make map! []]]
show [s/eval db reduce ["select ?" 'word]]
show [s/eval db ["select ?" o/y]]
show [s/eval db ["select ?" :o/y]]
show [s/eval db ["select ?" "ěšč"]]
show [s/eval db ["select ?, ?, ?, ?, ?" 1 2.5 "x" #(none) #(true)]]
show [s/eval db ["select ?" #(false)]]
show [s/eval db ["select ?" #{01}]]
show [s/eval db ["select ?" [1]]]
show [s/eval db ["select ?" foo]]
show [s/exec db "create table p(a, b, c)"]
show [s/eval db ["insert into p values (?, ?, ?)" 1 2 3 4 5 6]]
show [s/eval db ["insert into p values (?, ?, ?)" 7 8]]
show [s/eval db ["insert into p values (?, ?, ?)" [10 11 12] [13 14] [15 16 17 18]]]
show [s/eval db ["insert into p values (?, ?, ?)" [20 21 22] 23 24 25]]
show [s/eval db "select * from p order by rowid"]
show [s/eval db ["insert into p values (?, ?, ?)" [30 31 32] [33 #{01} 34]]]
show [s/eval db "select count(*) from p"]
show [s/exec db "create table q(a not null)"]
show [s/eval db ["insert into q values (?)" 1 #(none) 2]]
show [s/eval db "select * from q"]
show [s/last-insert-id db]

print "--- prepare, step, columns, reset, finalize"
st: s/prepare db "select a, b from t order by a"
show [type? :st]
show [mold :st]
print ["info/of stmt:" mold shape-of s/info/of st]
show [s/columns st]
show [s/step st]
print ["info/of stmt:" mold shape-of s/info/of st]
show [s/step st]
show [s/step st]
show [s/step st]
show [s/step st]
show [s/reset st]
show [s/step/rows st 2]
show [s/step/rows st 2]
show [s/reset st]
show [s/step/rows st 0]
show [s/reset st]
show [s/step/rows st -1]
show [s/reset st]
show [s/step/rows st 1]
show [s/reset st]
show [s/step/rows st 100]
show [s/step/rows st 100]
show [s/finalize st]
print ["info/of stmt:" mold shape-of s/info/of st]
show [s/step st]
show [s/columns st]
show [s/reset st]
show [s/finalize st]
show [s/eval db st]
show [s/eval db reduce [st]]
show [s/eval db reduce [st 1]]
show [s/prepare db "nonsense"]
show [s/prepare db next "Xselect 1"]
empty-st: s/prepare db ""
show [type? :empty-st]
show [s/step empty-st]
show [s/columns empty-st]
show [s/columns s/prepare db "create table cc(a)"]
show [s/columns s/prepare db "select 1 as one, 2, x'01' as b"]
show [s/columns s/prepare db "select randomblob(4)"]

print "--- many rows"
many: s/prepare db "with recursive c(x) as (select 1 union all select x + 1 from c limit 1500) select x from c"
r: s/step/rows many 1001
show [reduce [length? r first r last r]]
r: s/step/rows many 1001
show [reduce [length? r first r last r]]
show [s/step/rows many 1001]
show [s/reset many]
r: s/step/rows many 0
show [reduce [length? r first r last r]]
show [s/finalize many]
show [length? s/eval db "with recursive c(x) as (select 1 union all select x + 1 from c limit 1500) select x from c"]

print "--- step/with"
w: s/prepare db "select ?, ?"
show [s/step/with w [1 2]]
show [s/reset w]
show [s/step/with w [1]]
show [s/reset w]
show [s/step/with w []]
show [s/reset w]
show [s/step/with w [1 2 3]]
show [s/reset w]
show [s/step/with w [1.5 "x"]]
show [s/reset w]
show [s/step/with w [#(none) #(true)]]
show [s/reset w]
show [s/step/with w [#(false) "ěšč"]]
show [s/reset w]
show [s/step/with w reduce [next "xA_di" next "ěšč"]]
show [s/reset w]
show [s/step/with w [#{01} 1]]
show [s/reset w]
show [s/step/with w [[1] 1]]
show [s/reset w]
show [s/step/with w [unset-word 1]]
show [s/reset w]
show [s/step/with w ['x 1]]
show [s/reset w]
show [s/step/with w [1 2-Nov-2026]]
show [s/reset w]
show [s/step/with w [x :x]]
show [s/reset w]
show [s/step/with w [o/y 1]]
show [s/reset w]
show [s/step/with w [:o/y 1]]
show [s/reset w]
show [s/step/with w [(1 + 1) 1]]
show [s/reset w]
show [s/step w]
show [s/step/with w [5 6]]
show [s/step/with w [7 8]]
show [s/finalize w]
named: s/prepare db "select :a, @b, $c, ?4"
show [s/step/with named [1 2 3 4]]
show [s/finalize named]

print "--- stepping after done"
ro: s/prepare db "select 1"
show [s/step ro]
show [s/step ro]
show [s/step ro]
show [s/step ro]
print ["info/of stmt:" mold shape-of s/info/of ro]
show [s/reset ro]
show [s/step ro]
show [s/finalize ro]
wr: s/prepare db "insert into t values (9, 'nine')"
show [s/step wr]
show [s/step wr]
show [s/step wr]
show [s/eval db "select count(*) from t where a = 9"]
show [s/finalize wr]
bad: s/prepare db "insert into q values (null)"
show [s/step bad]
show [s/step bad]
show [s/finalize bad]
s/exec db "create table u(a unique)"
dup: s/prepare db "insert into u values (1)"
show [s/step dup]
show [s/step dup]
show [s/finalize dup]
ev: s/prepare db "select a from t order by a"
show [s/eval db ev]
show [s/eval db ev]
show [s/step ev]
show [s/eval db reduce [ev]]
show [s/finalize ev]
lk: s/prepare db "select b from t where a = ?"
show [s/eval db reduce [lk 1]]
show [s/eval db reduce [lk 2]]
show [s/eval db reduce [lk 99]]
show [s/eval db reduce [lk 3]]
show [s/finalize lk]

print "--- handles of the wrong kind"
st: s/prepare db "select 1"
k: rc4/key #{0102}
show [s/exec st "select 1"]
show [s/eval st "select 1"]
show [s/prepare st "select 1"]
show [s/last-insert-id st]
show [s/trace st 0]
show [s/close st]
show [s/step db]
show [s/columns db]
show [s/reset db]
show [s/finalize db]
show [s/eval db db]
show [s/eval db reduce [db]]
show [s/exec k "select 1"]
show [s/step k]
show [s/eval db k]
show [s/eval db reduce [k]]
show [s/info/of k]
show [s/finalize st]

print "--- the library"
show [s/shutdown]
show [s/shutdown]
show [s/initialize]
show [s/initialize]
show [s/eval db "select count(*) from t"]

print "--- close"
show [s/close db]
show [s/close db]
print ["info/of db:" mold shape-of s/info/of db]
