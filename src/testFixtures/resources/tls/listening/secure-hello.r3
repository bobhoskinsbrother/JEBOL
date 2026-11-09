Rebol [
	title: "A listening tls server answering one client, both in one script"
	purpose: {Run on ./r3-head to write secure-hello.txt, in the folder of the certificate fixtures.}
]

said: copy []
fixtures: %../../org/jebol/tls/
server: open compose/deep [
	scheme: 'tls
	port: 0
	config: [
		certificates: [(fixtures/localhost-rsa.pem) (fixtures/test-intermediate.pem)]
		private-key: (fixtures/localhost-rsa.key)
	]
]
port-number: query server/state/tcp-port 'local-port
append said reduce ['listening integer? port-number port-number > 0]

client: open join tls://localhost: port-number
client/awake: func [event] [
	append said reduce ['client event/type]
	switch event/type [
		connect [write event/port "GET / HTTP/1.0^M^/^M^/"]
		wrote [read event/port]
		read [append said to string! event/port/data close event/port return true]
		error [return true]
	]
	false
]

wait [client 10]
close server/state/tcp-port
print mold said
