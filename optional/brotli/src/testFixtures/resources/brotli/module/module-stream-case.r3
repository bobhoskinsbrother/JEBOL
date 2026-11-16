Rebol []
args: any [system/options/args []]
b: either 2 = length? args [import (to-rebol-file first args)] [import 'brotli]
index: to integer! last args
words: "the quick brown fox jumps over a lazy dog "
text: func [n /local out x] [
	out: make binary! n  x: 12345
	loop n [
		x: (x * 1103515245 + 12345) and 2147483647
		append out to integer! pick words 1 + (remainder shift x -16 length? words)
	]
	out
]
answer-of: func [r [any-type!]] [
	case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		all [binary? :r 48 < length? r] [ajoin ["binary of " length? r " octets, crc " checksum r 'crc32]]
		handle? :r [ajoin ["handle " r/type]]
		block? :r [ajoin ["[" form collect [foreach each r [keep answer-of :each]] "]"]]
		true [mold/flat :r]
	]
]
in-chunks: func [level chunk finish-with /local e out at] [
	e: b/make-encoder/level level
	out: copy #{}
	at: t
	while [not tail? at] [
		b/write e copy/part at chunk
		at: skip at chunk
	]
	append out b/write/finish e finish-with
	reduce [out equal? t b/decompress out]
]
flushed-every: func [level chunk /local e out at] [
	e: b/make-encoder/level level
	out: copy #{}
	at: t
	while [not tail? at] [
		append out b/write/flush e copy/part at chunk
		at: skip at chunk
	]
	append out b/write/finish e #{}
	reduce [out equal? t b/decompress out]
]
read-every: func [level chunk /local e out at] [
	e: b/make-encoder/level level
	out: copy #{}
	at: t
	while [not tail? at] [
		b/write e copy/part at chunk
		append out b/read e
		at: skip at chunk
	]
	append out b/write/finish e #{}
	reduce [out equal? t b/decompress out]
]
decoded-in-chunks: func [chunk /local d out at] [
	d: b/make-decoder
	out: copy #{}
	at: c
	while [not tail? at] [
		b/write d copy/part at chunk
		append out b/read d
		at: skip at chunk
	]
	reduce [length? out equal? out t d/finished]
]
hello: {hello hello hello hello}
t: text 300000
c: b/compress t

cases: copy []
foreach level [0 1 2 5 9 11] [
	foreach chunk [1000 65536 98304 98305] [
		append/only cases compose [in-chunks (level) (chunk) #{}]
		append/only cases compose [in-chunks (level) (chunk) none]
	]
	append/only cases compose [flushed-every (level) 50000]
	append/only cases compose [read-every (level) 70000]
	append/only cases compose [b/read b/write b/make-encoder/level (level) hello]
	append/only cases compose [b/write b/write b/make-encoder/level (level) hello none]
]
foreach chunk [1 7 1000 65536] [append/only cases compose [decoded-in-chunks (chunk)]]
s: b/compress hello
repeat cut length? s [
	append/only cases compose [length? b/write/flush b/make-decoder copy/part s (cut)]
]

if index = 0 [print length? cases quit]
code: pick cases index
prin [mold/flat code "=> "]
set/any 'r try code
print answer-of :r
