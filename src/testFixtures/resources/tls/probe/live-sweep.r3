Rebol [
	title: "One url of the TLS handshake sweep against live servers"
	purpose: {Run by hand, never by a gate: live-sweep.sh runs it once per url on ./r3-head and the jar.}
]

url: to url! trim either block? system/script/args [first system/script/args] [system/script/args]
answer: try [read/part url 64]
print [
	url "->"
	either error? answer [
		rejoin [answer/id ": " any [answer/arg1 ""]]
	][
		rejoin ["read " length? answer " bytes"]
	]
]
