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
hex-digits: charset "0123456789abcdef"
shape-of: func [molded [string!] /local out] [
	out: copy molded
	parse out [any [remove ["0#" some hex-digits] insert "0#<address>" | skip]]
	out
]
answer-of: func [r [any-type!]] [
	case [
		unset? :r ["unset"]
		error? :r [ajoin ["error " r/type "/" r/id " " mold/flat r/arg1]]
		all [binary? :r 48 < length? r] [ajoin ["binary of " length? r " octets, crc " checksum r 'crc32]]
		handle? :r [ajoin ["handle " shape-of mold :r]]
		true [mold/flat :r]
	]
]
show: func [code [block!] /local r] [
	set/any 'r try code
	print [mold/flat code "=>" answer-of :r]
]
hello: {hello hello hello hello}

scenarios: [
	[
		print "--- an encoder's fields"
		e: b/make-encoder
		show [e]
		show [e/type]
		show [e/finished]
		show [e/mode]
		show [e/size-hint]
		show [e/nonsense]
		show [e/finished: true]
		show [e/mode: 1]
		show [e/mode: "text"]
		show [e/size-hint: 23]
		show [e/nonsense: 1]
	][
		print "--- one encoder through every operation"
		e: b/make-encoder
		show [same? e b/write e hello]
		show [e/finished]
		show [b/read e]
		show [b/read e]
		show [b/write/flush e hello]
		show [b/write/flush e #{}]
		show [b/write/flush e #{}]
		show [b/write e none]
		show [e/finished]
		show [b/write e hello]
		show [b/write/flush e hello]
		show [b/write/finish e hello]
		show [b/write e none]
		show [b/read e]
	][
		print "--- finishing at each level"
		foreach level [0 1 2 5 6 9 10 11 -1 -2 12] [
			e: b/make-encoder/level level
			print ["make-encoder/level" level answer-of b/write/finish e hello]
		]
		show [b/write/finish b/make-encoder hello]
		show [b/write/finish b/make-encoder #{}]
		show [b/write/finish b/make-encoder ""]
		show [b/write/finish b/make-encoder "ěšč"]
		show [b/write/finish b/make-encoder next "Xhello"]
	][
		print "--- an encoder with nothing written"
		show [b/write b/make-encoder none]
		e: b/make-encoder
		show [b/read e]
		show [b/write e none]
		show [e/finished]
		e: b/make-encoder
		show [b/write/flush e none]
		show [b/write e none]
	][
		print "--- small flushes"
		e: b/make-encoder
		show [b/write/flush e "a"]
		show [b/write/flush e "b"]
		show [b/write/finish e "c"]
	][
		print "--- mode"
		foreach mode [0 1 2 3 -1] [
			e: b/make-encoder/level 11
			set/any 'r try [e/mode: mode]
			print ["mode" mode answer-of :r answer-of b/write/finish e text 20000]
		]
	][
		print "--- size hint"
		foreach hint [0 1000 20000 1000000 -1] [
			e: b/make-encoder/level 5
			set/any 'r try [e/size-hint: hint]
			print ["size-hint" hint answer-of :r answer-of b/write/finish e text 20000]
		]
		e: b/make-encoder/level 5
		b/write e text 1000
		show [e/size-hint: 20000]
		show [b/write/finish e text 19000]
	][
		print "--- a decoder's fields"
		d: b/make-decoder
		show [d]
		show [d/type]
		show [d/finished]
		show [d/mode]
		show [d/mode: 1]
		show [d/finished: true]
	][
		print "--- one decoder through a stream in pieces"
		c: b/compress text 300000
		d: b/make-decoder
		show [b/read d]
		show [same? d b/write d copy/part c 10]
		show [b/read d]
		show [length? b/write/flush d copy/part skip c 10 1000]
		show [length? b/write/finish d skip c 1010]
		show [d/finished]
		show [b/write d #{00}]
		show [b/read d]
		show [b/write d none]
	][
		print "--- a decoder given a whole stream"
		d: b/make-decoder
		show [b/write/finish d b/compress hello]
		show [d/finished]
		show [b/write d none]
	][
		print "--- a decoder given what is not a stream"
		d: b/make-decoder
		show [b/write/finish d #{FFFFFFFF}]
		show [d/finished]
		d: b/make-decoder
		show [b/write/finish d copy/part b/compress text 300000 100]
		show [d/finished]
		d: b/make-decoder
		show [b/write d none]
	][
		print "--- handles of the wrong kind"
		k: rc4/key #{0102}
		show [b/write k hello]
		show [b/read k]
		show [k/finished]
		show [b/write b/make-decoder b/make-encoder]
	]
]

if index = 0 [print length? scenarios quit]
do pick scenarios index
