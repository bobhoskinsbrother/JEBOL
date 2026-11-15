Rebol [
	Title: "What a build without R3's Bulk compressions answers for each of them"
	Usage: {
		scripts/build-r3.sh core r3-core
		mkdir fresh && cp scripts/core-compressions.r3 fresh/ && ./r3-core fresh/core-compressions.r3
	}
	Purpose: {
		Settles what COMPRESS and DECOMPRESS answer for a method the build
		lacks (goal 2a step 6, docs/rebol-findings.md 252). Run it on
		./r3-core and on ./r3-head: the first is the product JEBOL's core
		matches, the second the one its shipped jar matches.
	}
]
print mold system/catalog/compressions
foreach method [br crush lz4 lzav lzma lzw] [
	foreach action [compress decompress] [
		raised: try [
			either action = 'compress [compress "test" method] [decompress #{00010203} method]
		]
		print [action method either error? raised [mold reduce [raised/type raised/id raised/arg1]] [mold raised]]
	]
]
print [system/version system/product]
