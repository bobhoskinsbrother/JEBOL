Rebol []
recycle/off
b: import (to-rebol-file system/options/args/1)
noise: func [n /local out x] [
	out: make binary! n  x: 12345
	loop n [
		x: (x * 1103515245 + 12345) and 2147483647
		append out (to integer! x / 65536) and 255
	]
	out
]
all-noise: noise 300000
print checksum all-noise 'sha1
foreach chunks [1 10 30 33 34 40 50 66 100] [
	e: b/make-encoder/level 1
	got: copy []
	whole: copy #{}
	repeat at chunks [b/write :e copy/part skip all-noise at - 1 * 3000 3000]
	loop 3 [r: b/read :e  append got length? r  append whole r]
	r: b/write/finish :e #{}
	append got length? r
	append whole r
	print [chunks mold got length? whole checksum whole 'sha1 (chunks * 3000) = length? decompress whole 'bzip2]
]
d: b/make-decoder
c: b/compress all-noise
half: (length? c) / 2
got: copy []
b/write :d copy/part c half
append got length? b/read :d
b/write :d skip c half
append got length? b/read :d
append got length? b/read :d
print [mold got]
