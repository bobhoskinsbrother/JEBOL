Rebol []
if all [block? system/options/args 2 = length? system/options/args] [
	import (to-rebol-file system/options/args/1)
	import (to-rebol-file system/options/args/2)
]
answer-of: func [r [any-type!] /local text] [
	text: case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1 " " mold/flat r/arg2]]
		port? :r ["a port"]
		true [mold/flat :r]
	]
	replace/all text mold what-dir "%./"
]
show: func [code [block!] /local r] [
	set/any 'r try code
	print [mold/flat code "=>" answer-of :r]
]

print "--- the scheme"
show [select system/schemes/sqlite 'title]
show [exists? %new.db]
show [open sqlite:new.db]
show [open/new sqlite:not-exists/dir]
db: open/new sqlite:new.db
show [exists? %new.db]
show [open? db]
show [db/spec/path]
show [type? db/state/db]
show [write db "create table cars(id integer primary key, name text); insert into cars values (1, 'Audi'), (2, 'Bentley'), (3, 'Citroen'), (4, 'Dacia'), (5, 'Elan')"]
show [write db "select count(*) as n from cars"]
show [write db "nonsense"]

print "--- insert, take, read, pick"
show [same? db insert db "select id, name from cars order by id"]
show [length? db/state/statements]
show [take db]
show [take db]
show [take/part db 2]
show [take db]
show [take db]
show [take db]
show [same? db insert db "select id, name from cars order by id"]
show [length? db/state/statements]
show [read/part db 2]
show [read db]
show [read db]
show [same? db insert db "select id, name from cars order by id"]
show [read db]
show [insert db "nonsense"]
show [pick db "select name from cars where id > 3"]
show [length? db/state/statements]
show [pick db "select name from cars where id > 99"]
show [same? db/data db/data]
show [pick db "insert into cars values (6, 'Ford')"]
show [pick db "select count(*) from cars"]
show [pick db "insert into cars values (7, 'Gilbern')"]
show [pick db "select count(*) from cars"]

print "--- modify"
show [modify db 'trace-level 1]
show [db/state/trace-level]
show [pick db "select 1"]
show [modify db 'trace-level 0]
show [modify db 'other 1]
show [pick db "select 2"]

print "--- close"
show [same? db close db]
show [open? db]
show [db/state/db]
show [length? db/state/statements]
show [close db]
show [write db "select 1"]
show [insert db "select 1"]
show [read db]
show [take db]
show [pick db "select 1"]

print "--- reopen"
db: open sqlite:new.db
show [pick db "select name from cars order by id desc"]
close db
db: open sqlite:./new.db
show [pick db "select count(*) from cars"]
close db
db: open/new to url! "sqlite:"
show [db/spec/path]
close db
show [exists? %.db]
