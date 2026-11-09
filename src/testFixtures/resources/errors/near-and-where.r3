Rebol [
	title: "NEAR and WHERE of errors natives raise, two ways"
	purpose: {Run by hand on ./r3-head and the jar; goal 1e's sweep starts from what it finds.}
]

show: func [label failure] [
	print [label "->" failure/id "| near:" mold/flat failure/near "| where:" mold/flat failure/where]
]

show "raised inside the native" try [compress "a" 'brotli]
show "refused before the native runs" try [if false log-trace 'SMTP ["x"] 5]
show "refused before the native runs, unset from a paren" try [if false () 6]
