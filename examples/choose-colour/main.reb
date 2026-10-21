Rebol []

print "Pick a colour. Cancelling the chooser ends this."

suggested: 0.128.255

while [colour: request-color/default suggested] [
    print ["chosen" colour]
    suggested: colour
]

print "no colour chosen"
