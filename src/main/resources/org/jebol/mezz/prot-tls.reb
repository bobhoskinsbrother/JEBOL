REBOL [
	title: "TLS scheme, carried by the host's own TLS"
	name: 'tls
	file: %prot-tls.reb
	purpose: {
		R3's tls scheme as a script sees it -- the port, its extra fields, its
		events, the upgrade of an open connection and the listening server --
		with the handshake and the records done by the host. See decision 65.
	}
]

_log-error: func [msg] [sys/log/error 'TLS msg]
_log-info: func [msg] [
	if block? msg [msg: reform msg]
	print rejoin [" ^[[1;33m[TLS] ^[[36m" msg "^[[0m"]
]
_log-more: func [msg] [
	if block? msg [msg: reform msg]
	print rejoin [" ^[[33m[TLS] ^[[0;36m" msg "^[[0m"]
]
log-error: log-info: log-more: none
tls-verbosity: func [
	{Sets the log verbosity level for TLS-related messages and debug output.}
	verbose [integer!] "Verbosity level"
] [
	log-error: log-info: log-more: none
	case/all [
		verbose >= 0 [log-error: :_log-error]
		verbose >= 1 [log-info: :_log-info]
		verbose >= 2 [log-more: :_log-more]
	]
]
log-error: :_log-error

dispatch-event: function [
	event [word!]
	target [port!]
] [
	either all [
		port? target/parent
		function? :target/parent/awake
	] [
		target/parent/awake make event! [type: event port: target]
	] [
		insert system/ports/system make event! [type: event port: target]
	]
]

TLS-context: object [
	tcp-port: none
	tls-port: none
	session: none
	server?: false
	connected?: false
	error: none
	port-data: none
	waiting-to-send: none
	writing?: false
	reading?: false
	read-wanted?: false
	asked-to-read?: false
	write-to-report?: false
]

make-TLS-context: func [conn [port!] port [port! none!] started [handle!] serving? [logic!]] [
	make TLS-context [
		tcp-port: conn
		tls-port: port
		session: :started
		server?: serving?
		port-data: make binary! 4096
		waiting-to-send: make block! 4
	]
]

end-TLS-with: func [ctx [object!] reason [string!]] [
	ctx/error: make error! [type: 'Access id: 'Protocol arg1: reason]
	if :log-error [log-error ctx/error]
]

write-next-to-network: func [ctx [object!]] [
	if any [ctx/writing? empty? ctx/waiting-to-send] [exit]
	ctx/writing?: true
	write ctx/tcp-port take ctx/waiting-to-send
]

keep-reading: func [
	ctx [object!]
] [
	if ctx/reading? [exit]
	either any [ctx/writing? not empty? ctx/waiting-to-send] [
		ctx/read-wanted?: true
	] [
		ctx/read-wanted?: false
		ctx/reading?: true
		read ctx/tcp-port
	]
]

writes-drained: func [
	ctx [object!]
] [
	ctx/writing?: false
	unless empty? ctx/waiting-to-send [
		write-next-to-network ctx
		return false
	]
	if any [ctx/read-wanted? not ctx/connected?] [keep-reading ctx]
	true
]

apply-exchange: func [
	ctx [object!]
	answered [block!]
	/local to-network arrived state reason events
] [
	set [to-network arrived state reason] answered
	events: copy []
	unless empty? to-network [append ctx/waiting-to-send to-network]
	write-next-to-network ctx
	if reason [
		end-TLS-with ctx reason
		return copy [error]
	]
	unless empty? arrived [
		append ctx/port-data arrived
		ctx/asked-to-read?: false
		append events 'read
	]
	if all [state = 'open not ctx/connected?] [
		ctx/connected?: true
		insert events 'connect
	]
	if all [state <> 'closed any [not ctx/connected? ctx/asked-to-read?]] [
		keep-reading ctx
	]
	events
]

report-to-TLS-port: func [ctx [object!] events [block!] /local port] [
	port: either ctx/server? [ctx/tcp-port] [ctx/tls-port]
	if all [ctx/tls-port not ctx/server?] [ctx/tls-port/data: ctx/port-data]
	foreach event events [
		either ctx/server? [
			if event = 'read [ctx/tcp-port/parent/actor/On-Read ctx/tcp-port]
		] [
			dispatch-event event port
		]
	]
	not empty? events
]

TLS-client-awake: function [
	event [event!]
] [
	TCP-port: event/port
	ctx: TCP-port/extra
	switch/default event/type [
		lookup [
			open TCP-port
			false
		]
		connect [
			report-to-TLS-port ctx apply-exchange ctx tls-exchange ctx/session 'begin none
		]
		wrote [
			if all [writes-drained ctx ctx/write-to-report?] [
				ctx/write-to-report?: false
				dispatch-event 'wrote ctx/tls-port
			]
			false
		]
		read [
			ctx/reading?: false
			arrived: copy TCP-port/data
			clear TCP-port/data
			report-to-TLS-port ctx apply-exchange ctx tls-exchange ctx/session 'receive arrived
		]
		close [
			unless ctx/connected? [
				end-TLS-with ctx join TCP-port/spec/host " closed the connection during the handshake"
			]
			dispatch-event 'close ctx/tls-port
			true
		]
		error [
			unless ctx/error [
				ctx/error: make error! [type: 'access id: 'protocol arg1: TCP-port/spec/ref]
			]
			dispatch-event 'error ctx/tls-port
			true
		]
	] [
		close TCP-port
		do make error! rejoin ["Unexpected TLS event: " event/type]
	]
]

TLS-server-awake: func [
	event [event!]
	/local port serv
] [
	if event/type = 'accept [
		serv: event/port
		port: first serv
		port/extra: make-TLS-context port serv/parent
			tls-server serv/extra/certificates serv/extra/private-key
			true
		port/spec/title: "TLS Server's client"
		port/awake: :TLS-server-client-awake
		port/extra/asked-to-read?: true
		keep-reading port/extra
	]
	false
]

TLS-server-client-awake: function [
	event [event!]
] [
	TCP-port: event/port
	ctx: TCP-port/extra
	switch event/type [
		read [
			ctx/reading?: false
			arrived: copy TCP-port/data
			clear TCP-port/data
			events: apply-exchange ctx tls-exchange ctx/session 'receive arrived
			if find events 'error [
				close TCP-port
				return true
			]
			report-to-TLS-port ctx events
			false
		]
		wrote [
			if all [writes-drained ctx ctx/write-to-report?] [
				ctx/write-to-report?: false
				TCP-port/parent/actor/On-Wrote TCP-port
			]
			false
		]
		close [true]
	]
]

do-TLS-open: func [
	port [port!]
	/local spec conn config certs der key
] [
	if port/state [return port]
	spec: port/spec
	either port? conn: select spec 'conn [
		spec/host: conn/spec/host
		spec/port: conn/spec/port
		if block? spec/ref [
			spec/ref: rejoin [tls:// any [spec/host ""] ":" spec/port]
		]
	] [
		conn: make port! [
			scheme: 'tcp
			host: spec/host
			port: spec/port
			ref: rejoin [tcp:// any [host ""] ":" port]
		]
		conn/parent: port
	]
	either spec/host [
		port/extra: conn/extra: make-TLS-context conn port tls-client spec/host spec/port false
		port/data: conn/extra/port-data
		conn/awake: :TLS-client-awake
		either open? conn [
			report-to-TLS-port conn/extra apply-exchange conn/extra tls-exchange conn/extra/session 'begin none
		] [
			open conn
		]
	] [
		spec/ref: rejoin [tls://: spec/port]
		port/spec/title: "TLS Server"
		conn/spec/title: "TLS Server (internal)"
		port/state: conn/extra: object [
			TCP-port: conn
			certificates: copy []
			private-key: none
		]
		if config: select spec 'config [
			certs: any [select config 'certificates []]
			unless block? certs [certs: to block! certs]
			foreach file certs [
				try/with [
					der: select decode 'pkix read file 'binary
					append port/state/certificates der
				] [
					log-error ["Failed to import certificate:" file]
				]
			]
			if key: select config 'private-key [
				if file? key [try [key: load key]]
				either handle? key [
					port/state/private-key: key
				] [log-error ["Failed to import private key:" key]]
			]
		]
		port/actor: context [
			On-Read: func [port [port!] /local data] [
				probe to string! data: port/extra/port-data
				either empty? data [
					do-TLS-read port
				] [
					do-TLS-write port {HTTP/1.1 200 OK^M
Content-type: text/plain^M
^M
Hello from Rebol using TLS v1.3}
				]
			]
			On-Wrote: func [port [port!]] [
				dispatch-event 'close port
			]
		]
		conn/parent: port
		conn/awake: :TLS-server-awake
		open conn
	]
	port
]

do-TLS-close: func [
	port [port!] /local ctx parent
] [
	unless ctx: port/extra [return port]
	parent: port/parent
	close ctx/tcp-port
	ctx/tcp-port/awake: none
	ctx/tcp-port: none
	ctx/tls-port: none
	port/extra: none
	if parent [
		insert system/ports/system make event! [type: 'close port: parent]
	]
	port
]

do-TLS-read: func [
	port [port!]
] [
	port/extra/asked-to-read?: true
	keep-reading port/extra
	port
]

do-TLS-write: func [
	port [port!]
	value [any-string! binary!]
	/local ctx
] [
	ctx: port/extra
	ctx/write-to-report?: true
	apply-exchange ctx tls-exchange ctx/session 'send to binary! value
	port
]

tls-config: func [
	spec
] [
	foreach [key value] spec [
		switch :key [
			verbose
			verbosity
			[
				tls-verbosity :value
			]
		]
	]
]

sys/make-scheme [
	name: 'tls
	title: "TLS protocol v1.3"
	spec: make system/standard/port-spec-net []
	actor: reduce/no-set [
		read: :do-TLS-read
		write: :do-TLS-write
		open: :do-TLS-open
		close: :do-TLS-close
		query: func [port [port!]] [all [port/extra query port/extra/tcp-port]]
		open?: func [port [port!]] [all [port/extra open? port/extra/tcp-port]]
		copy: func [port [port!]] [if port/data [copy port/data]]
		length?: func [port [port!]] [either port/data [length? port/data] [0]]
	]
	set-verbose: :tls-verbosity
	config: :tls-config
]
