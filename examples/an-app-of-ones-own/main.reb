REBOL [title: "Notes"]

notes: 0
window: view/no-wait/options [
    text "No notes yet"
    button "Add a note" do [
        notes: notes + 1
        print rejoin ["notes " notes]
    ]
] [title: "Notes"]

find-style: func [gob style /local found] [
    foreach sub any [gob/pane []] [
        if all [object? sub/data style = select sub/data 'style] [return sub/data]
        if found: find-style sub style [return found]
    ]
    none
]

loop 4 [wait [gui-event-port 0]]
adder: find-style window 'button
button-at: second map-gob-offset/reverse adder/gob adder/gob/size / 2
print rejoin ["button-at " button-at]
print rejoin ["button-in-window " button-at - window/offset]
print "open"
do-events
