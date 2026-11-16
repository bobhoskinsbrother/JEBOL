Rebol []
words: "the quick brown fox jumps over a lazy dog "
text: func [n /local out x] [
	out: make binary! n  x: 12345
	loop n [
		x: (x * 1103515245 + 12345) and 2147483647
		append out to integer! pick words 1 + (remainder shift x -16 length? words)
	]
	out
]
samples: [#{} #{00} #{0102} {hello hello hello hello}]
foreach n [1000 16385 98305 300000] [append samples text n]
encoding-before: copy system/schemes/http/headers/Accept-Encoding
by-the-core: collect [
	foreach sample samples [foreach level [0 1 2 3 4 5 6 7 8 9 10 11] [keep compress/level sample 'br level]]
]
b: either all [block? system/options/args not empty? system/options/args] [
	import (to-rebol-file system/options/args/1)
][
	import 'brotli
]
by-the-extension: collect [
	foreach sample samples [foreach level [0 1 2 3 4 5 6 7 8 9 10 11] [keep compress/level sample 'br level]]
]
by-its-command: collect [
	foreach sample samples [foreach level [0 1 2 3 4 5 6 7 8 9 10 11] [keep b/compress/level sample level]]
]
answer-of: func [r [any-type!]] [
	case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		all [binary? :r 48 < length? r] [ajoin ["binary of " length? r " octets, crc " checksum r 'crc32]]
		handle? :r [ajoin ["handle " mold r/type]]
		true [mold/flat :r]
	]
]
show: func [code [block!] /local r] [
	set/any 'r try code
	print [mold/flat code "=>" answer-of :r]
]
hello: {hello hello hello hello}

print "--- the module"
show [encoding-before]
show [equal? by-the-core by-the-extension]
show [equal? by-the-core by-its-command]
show [select spec-of b 'version]
show [select spec-of b 'title]
show [select spec-of b 'needs]
show [words-of b]
show [b/version]
show [type? b/version]
show [system/schemes/http/headers/Accept-Encoding]

print "--- compress"
show [b/compress hello]
foreach level [0 1 2 3 4 5 6 7 8 9 10 11 12 -1 -2 99] [
	print ["level" level answer-of b/compress/level hello level]
]
show [b/compress #{}]
show [b/compress #{00}]
show [b/compress #{0001}]
show [b/compress #{000102}]
show [b/compress ""]
show [b/compress "ěšč"]
show [b/compress next "Xhello"]
show [b/compress/part "abcdefghij" 5]
show [b/compress/part "abcdefghij" -3]
show [b/compress/part "abcdefghij" 100]
show [b/compress/part next "Xabcdefghij" 3]
foreach n [16383 16384 16385 65535 65536 65537 98303 98304 98305 4194303 4194304 4194305] [
	t: text n
	foreach level [0 1 2 5 9 10 11] [
		print ["size" n "level" level answer-of b/compress/level t level]
	]
]

print "--- compress 'br through the native, after import"
foreach level [0 1 6 11] [print ["native level" level answer-of compress/level hello 'br level]]
show [compress hello 'br]
show [equal? compress text 100000 'br b/compress text 100000]

print "--- decompress"
c: b/compress hello
show [b/decompress c]
show [b/decompress/size c 5]
show [b/decompress/size c 0]
show [b/decompress/size c 1000]
show [b/decompress/size c -1]
show [b/decompress/part c 5]
show [b/decompress/part c 0]
show [b/decompress/part c -1]
show [b/decompress #{}]
show [b/decompress #{00}]
show [b/decompress join c #{0102}]
show [b/decompress copy/part c (length? c) - 1]
show [b/decompress #{FFFFFFFF}]
show [b/decompress "abc"]
show [decompress c 'br]
show [decompress/size c 'br 5]
show [decompress #{} 'br]
print "--- decompress 'br refused, after import"
foreach bad [#{FFFFFFFF} #{00} #{11} #{040000} #{1C}] [show compose [decompress (bad) 'br]]
show [decompress copy/part c (length? c) - 1 'br]
big: b/compress text 300000
show [length? b/decompress big]
show [equal? text 300000 b/decompress big]
show [length? b/decompress/size big 100]
