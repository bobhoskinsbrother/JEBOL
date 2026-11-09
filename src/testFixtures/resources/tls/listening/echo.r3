Rebol [
	title: "A tcp server and a client in one script, the server echoing one line"
	purpose: {Run on ./r3-head to write echo.txt; JEBOL's test runs it and expects the same.}
]

server-saw: copy []
accepted-saw: copy []
client-saw: copy []
server: open tcp://:0
port-number: query server 'local-port

server/awake: func [event /local accepted] [
	append server-saw event/type
	if event/type = 'accept [
		accepted: first event/port
		accepted/awake: func [event] [
			append accepted-saw event/type
			switch event/type [
				read [write event/port join "echo: " to string! event/port/data]
				wrote [close event/port true]
			]
			false
		]
		read accepted
	]
	false
]

client: open join tcp://127.0.0.1: port-number
client/awake: func [event] [
	append client-saw event/type
	switch event/type [
		lookup [open event/port]
		connect [write event/port "hello"]
		wrote [read event/port]
		read [append client-saw to string! event/port/data close event/port return true]
	]
	false
]

wait [client 10]
close server
print mold/flat reduce [
	'listening integer? port-number port-number > 0
	'server server-saw
	'accepted accepted-saw
	'client client-saw
]
