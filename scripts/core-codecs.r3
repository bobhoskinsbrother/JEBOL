Rebol [
	Title: "What a build without LZMA answers for the archives in Rebol's codecs-test.r3"
	Usage: {
		scripts/build-r3.sh core r3-core
		mkdir fresh && cp scripts/core-codecs.r3 src/testFixtures/resources/archives/*.zip src/testFixtures/resources/archives/*.swf fresh/
		./r3-core fresh/core-codecs.r3
	}
	Purpose: {
		Settles what the SWF and ZIP codecs answer for an LZMA-compressed file
		on a build that lacks LZMA, and that the error is not caught on its way
		out of an assertion (goal 2a step 6, docs/rebol-findings.md 253). Run
		it on ./r3-core and on ./r3-head.
	}
]
import 'swf
codecs/swf/verbose: 0
system/options/log/zip: 0
show: func [label [string!] code [block!] /local answer] [
	answer: try code
	print [label either error? answer [mold reduce [answer/type answer/id answer/arg1]] [mold type? answer]]
]
show "load test1-deflate.swf" [load %test1-deflate.swf]
show "load test2-lzma.swf" [load %test2-lzma.swf]
show "load test-deflate.zip" [load %test-deflate.zip]
show "load test-stored.zip" [load %test-stored.zip]
show "load test-lzma.zip" [load %test-lzma.zip]
show "decode/info test-lzma.zip" [codecs/zip/decode/info read %test-lzma.zip]
reached: false
show "a block asserting the LZMA load" [
	assert-like: func [assertion [logic! none! error!]] [assertion]
	assert-like object? load %test2-lzma.swf
	reached: true
]
print ["the line after it ran:" reached]
print [system/version system/product]
