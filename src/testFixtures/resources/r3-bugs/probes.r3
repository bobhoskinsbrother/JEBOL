Rebol [Title: "The five Rebol bugs goal 1k fixes, and the boundaries around them"]

show: func [label [string!] answer [any-type!]] [
	print [label "|" case [
		error? :answer [mold/flat reduce ['error answer/id answer/arg1]]
		port? :answer ['port]
		image? :answer [mold/flat reduce ['image answer/size to binary! answer/rgb answer/alpha]]
		true [mold/flat :answer]
	]]
]

probe-each: func [probes [block!] /local label code] [
	foreach [label code] probes [show label try code]
]

probe-each [
	"find 0"                      [find #{00FF} 0]
	"find 1"                      [find #{0001} 1]
	"find 65 uncased"             [find #{0061} 65]
	"find 97 uncased"             [find #{0041} 97]
	"find/case 97"                [find/case #{0041} 97]
	"find 127"                    [find #{007F} 127]
	"find 128"                    [find #{0080} 128]
	"find 200"                    [find #{00C8} 200]
	"find 254"                    [find #{00FE} 254]
	"find 255"                    [find #{00FF} 255]
	"find 255 absent"             [find #{0001} 255]
	"find 256"                    [find #{00FF} 256]
	"find -1"                     [find #{00FF} -1]
	"find/case 255"               [find/case #{00FF} 255]
	"find/tail 255"               [find/tail #{00FF01} 255]
	"find/match 255"              [find/match #{FF00} 255]
	"find/last 255"               [find/last #{FF00FF00} 255]
	"find/reverse 255"            [find/reverse tail #{FF00FF00} 255]
	"find/skip 255"               [find/skip #{00FFFF00} 255 2]
	"find/part 255 inside"        [find/part #{00FF} 255 2]
	"find/part 255 outside"       [find/part #{00FF} 255 1]
	"find char 255"               [find #{00FF} #"^(FF)"]
	"find char 128"               [find #{0080} #"^(80)"]
	"find binary FF"              [find #{00FF} #{FF}]
	"select 254"                  [select #{FE01} 254]
	"select 255"                  [select #{FF01} 255]
	"select 0"                    [select #{00FF} 0]
	"find string 255"             [find "a^(FF)" 255]
	"find string char 255"        [find "a^(FF)" #"^(FF)"]
	"find string 98"              [find "ab" 98]
	"find string 233"             [find "a^(E9)" 233]
	"find wide string 233"        [find "^(E9)^(20AC)" 233]
	"find wide string 8364"       [find "a^(20AC)" 8364]
]

probe-each [
	"image block of two tuples"   [make image! [2x1 [1.2.3 4.5.6]]]
	"image block of four-tuples"  [make image! [2x1 [1.2.3.0 4.5.6.255]]]
	"image block too short"       [make image! [2x1 [1.2.3]]]
	"image block too long"        [make image! [2x1 [1.2.3 4.5.6 7.8.9]]]
	"image block empty"           [make image! [2x1 []]]
	"image block not tuples"      [make image! [2x1 [1.2.3 x]]]
	"image block then more"       [make image! [2x1 [1.2.3 4.5.6] 0]]
	"image zero size block"       [make image! [0x0 []]]
	"image tuple fill"            [make image! [2x1 1.2.3]]
	"image tuple fill four"       [make image! [2x1 1.2.3.4]]
	"image tuple and alpha"       [make image! [2x1 1.2.3 100]]
	"image binary"                [make image! [2x1 #{010203040506}]]
	"image binary and alpha"      [make image! [2x1 #{010203040506} #{0A0B}]]
	"image pixel set four-tuple"  [i: make image! 1x1 i/1: 1.2.3.4 i]
	"image append block"          [append make image! [1x1 0.0.0] [1.2.3 4.5.6.7]]
	"image change block"          [head change make image! [2x1 0.0.0] [1.2.3 4.5.6.7]]
]

i1: make image! [2x2 0.0.0]
i2: make image! [2x2 255.255.255]
i1/1: 255.255.255
i1/2: 255.255.255

probe-each [
	"diff whole"                  [image-diff i1 i2]
	"diff 0x0 1x1"                [image-diff/part i1 i2 0x0 1x1]
	"diff 0x0 1x2"                [image-diff/part i1 i2 0x0 1x2]
	"diff 0x0 1x3 past bottom"    [image-diff/part i1 i2 0x0 1x3]
	"diff 0x0 3x1 past right"     [image-diff/part i1 i2 0x0 3x1]
	"diff 0x0 3x3 past both"      [image-diff/part i1 i2 0x0 3x3]
	"diff 1x0 1x3 past bottom"    [image-diff/part i1 i2 1x0 1x3]
	"diff 0x1 2x2 past bottom"    [image-diff/part i1 i2 0x1 2x2]
	"diff 1x1 5x5 past both"      [image-diff/part i1 i2 1x1 5x5]
	"diff 0x0 2x2 exactly"        [image-diff/part i1 i2 0x0 2x2]
	"diff 1x0 1x2"                [image-diff/part i1 i2 1x0 1x2]
	"diff 2x2 -1x-2"              [image-diff/part i1 i2 2x2 -1x-2]
	"diff 2x2 -3x-3"              [image-diff/part i1 i2 2x2 -3x-3]
	"diff -1x0 2x2"               [image-diff/part i1 i2 -1x0 2x2]
	"diff 0x0 0x2 zero"           [image-diff/part i1 i2 0x0 0x2]
	"diff 3x0 1x2 outside"        [image-diff/part i1 i2 3x0 1x2]
	"diff 0x2 1x2 outside"        [image-diff/part i1 i2 0x2 1x2]
]

message: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}

cipher: func [name [string!] /local p] [
	p: open join crypt:// name
	modify p 'key head insert/dup copy #{} #{AB} either find name "128" [16] [32]
	modify p 'iv #{000102030405060708090A0B}
	modify p 'tag-length 16
	p
]

foreach name ["aes-128-gcm" "aes-128-ccm" "chacha20-poly1305"] [
	probe-each compose/deep [
		(join name " write read")            [p: cipher (name) write p message reduce [read p take p]]
		(join name " write take")            [p: cipher (name) write p message take p]
		(join name " write update read")     [p: cipher (name) write p message update p read p]
		(join name " two writes")            [p: cipher (name) write p copy/part message 16 write p skip message 16 reduce [read p take p]]
		(join name " aad 0")                 [p: cipher (name) modify p 'aad-length 0 write p message reduce [read p take p]]
		(join name " aad 4 own write")       [p: cipher (name) modify p 'aad-length 4 write p #{01020304} write p message reduce [read p take p]]
		(join name " aad 4 front of write")  [p: cipher (name) modify p 'aad-length 4 write p join #{01020304} message reduce [read p take p]]
		(join name " aad 4 short write")     [p: cipher (name) modify p 'aad-length 4 write p #{0102} write p message reduce [read p take p]]
		(join name " aad 32 whole message")  [p: cipher (name) modify p 'aad-length 32 write p message reduce [read p take p]]
	]
]

probe-each [
	"chacha header then message round trip" [
		p: cipher "chacha20-poly1305"
		write p #{0000000000000001}
		write p message
		sealed: read p
		tag: take p
		modify p 'direction 'decrypt
		write p #{0000000000000001}
		write p sealed
		reduce [message = read p tag = take p]
	]
]

write %held.txt "x"
probe-each [
	"file open twice"             [p: open %held.txt open p]
	"file open? after"            [open? p]
	"file delete while open"      [delete p]
	"file there after delete"     [exists? %held.txt]
	"file rename while open"      [rename p %renamed.txt]
	"file there after rename"     [reduce [exists? %held.txt exists? %renamed.txt]]
	"file create while open"      [create p]
	"file close"                  [close p]
	"file close again"            [close p]
	"file rename when closed"     [rename p %renamed.txt]
	"file renamed when closed"    [reduce [exists? %held.txt exists? %renamed.txt]]
	"file delete when closed"     [delete open/new %gone.txt]
	"file delete closed port"     [p: open %doomed.txt close p delete p]
	"file deleted closed port"    [exists? %doomed.txt]
	"file delete missing"         [p: open %missing.txt close p delete %missing.txt delete p]
]

make-dir %d/
probe-each [
	"dir open twice"              [d: open %d/ open d]
	"dir create while open"       [create d]
	"dir delete while open"       [delete d]
	"dir there after delete"      [exists? %d/]
	"dir close"                   [close d]
	"dir create when closed"      [d: open %e/ close d create d]
	"dir created when closed"     [exists? %e/]
]

probe-each [
	"crypt open twice"            [c: open crypt://aes-128-ecb open c]
	"checksum open twice"         [c: open checksum://sha1 open c]
	"clipboard open twice"        [c: open clipboard:// open c]
	"dns open twice"              [c: open dns:// open c]
	"console open twice"          [c: open console:// open c]
	"event open twice"            [c: open event:// open c]
]
