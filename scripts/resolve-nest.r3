REBOL [
	Title: "Resolve Rebol's Siskin nest into a plain spec for pre-make"
	Note: {
		Rebol builds with Siskin, a separate tool that reads make/rebol3.nest
		and drives both the pre-make step and the compiler. Nothing here needs
		the compiler part -- clang can be called directly -- so this reads the
		nest far enough to answer the one question pre-make.r3 asks: which
		source files, which mezzanine files and which configuration options
		this product is made of.

		Three constructs carry all of that. `name: [...]` defines a block,
		`:name` includes one, and `#if condition [...]` guards a piece of it.
		A key that names a list appends, and every other key is a plain value.
	}
]

arguments: system/script/args
if string? arguments [arguments: reduce [arguments]]
change-dir to-rebol-file first arguments
wanted: to word! second arguments
secure [file allow]

nest: load %rebol3.nest

;- every top-level `name: [...]` in the nest, so `:name` can be followed
named: make map! []
scan-names: func [block][
	while [not tail? block][
		either all [set-word? first block  block? second block][
			named/(to word! first block): second block
			block: skip block 2
		][	block: next block ]
	]
]
scan-names nest

;- this build is always macOS on arm64, so the platform tests are settled
holds?: func [test][
	all [word? :test  find [Posix? macOS?] test]
]

listed: [
	core-files host-files mezz-base-files mezz-sys-files
	mezz-lib-files mezz-prot-files boot-host-files config
]
spec: make map! []
foreach key listed [spec/:key: copy []]

;- a file list may hold `#if condition [...]` and may name another list
collect-into: func [into block][
	while [not tail? block][
		case [
			#if = first block [
				if holds? second block [collect-into into third block]
				block: skip block 2
			]
			block? first block [collect-into into first block]
			get-word? first block [
				collect-into into select named to word! first block
			]
			true [append into first block]
		]
		block: next block
	]
]

follow: func [block /local key value][
	while [not tail? block][
		case [
			#if = first block [
				if holds? second block [follow third block]
				block: skip block 2
			]
			get-word? first block [
				value: select named to word! first block
				if block? value [follow value]
			]
			set-word? first block [
				key: to word! first block
				value: second block
				;- `file:` adds one source and `files:` adds several, the same
				;- way `core-files:` adds a list. lz4 and xxhash arrive so.
				if find [file files] key [key: 'core-files]
				either find listed key [
					case [
						block? value    [collect-into spec/:key value]
						get-word? value [collect-into spec/:key select named to word! value]
						true            [append spec/:key value]
					]
				][	spec/:key: value ]
				block: next block
			]
		]
		block: next block
	]
]

;- The top-level `config:` is the enabled set rather than a menu of choices,
;- which two things settle. The shipped binary has `task!` and INCLUDE_TASK
;- appears nowhere else, and opt-dependencies.h derives every MBEDTLS_* option
;- from INCLUDE_MBEDTLS, which also appears nowhere else. Leaving it out builds
;- a binary that will not compile and then will not link.
follow nest
follow select named either 'bulk = wanted ['include-rebol-bulk]['include-rebol-core]

;- pre-make.r3 runs other scripts with DO, and DO of a file moves the working
;- directory, so every path it is given has to be absolute
spec/root:     clean-path %../
spec/source:   %src/
spec/version:  3.22.5
spec/product:  either 'bulk = wanted ['Bulk]['Core]
spec/os:       'macos
spec/arch:     'arm64
spec/compiler: 'clang

written: copy ajoin ["REBOL [Title: {generated " wanted " spec}]" newline]
foreach key [root source version product os arch compiler] [
	repend written [key ": " mold spec/:key newline]
]
foreach key listed [
	repend written [key ": " mold unique spec/:key newline]
]
write to file! third arguments written
print [
	"resolved" wanted "-"
	length? unique spec/core-files "core files,"
	length? unique spec/host-files "host files,"
	length? unique spec/config "options"
]
