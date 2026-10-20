REBOL [
	Title:   "Codec: UTC time as used in ASN.1 data structures (BER/DER)"
	Author:  "Oldes"
	Rights:  "Copyright (C) 2018 Oldes. All rights reserved."
	License: MIT
	Test:    %tests/units/crypt-test.r3
]

register-codec [
	name: 'utc-time
	type: 'time
	title: "UTC time as used in ASN.1 data structures (BER/DER)"
	decode: function [
		"Converts DER/BER UTC-time or generalized-time data to Rebol date! value"
		utc [binary! string!]
	][
		ch_digits: charset [#"0" - #"9"]
		utc: to string! copy utc
		zone: either #"Z" = last utc [1][5]
		if ((length? utc) - zone) < 14 [insert utc "20"]
		parse utc [
			  4 ch_digits   insert #"-"
			  2 ch_digits   insert #"-"
			  2 ch_digits   insert #"/"
			  2 ch_digits   insert #":"
			  2 ch_digits   insert #":" 
			[ 2 ch_digits | insert #"0"] ;seconds
			[
				remove #"Z" end
				|
				[#"-" | #"+"]
				2 ch_digits insert #":" 
			]
		]
		try [load utc]
	]
]
