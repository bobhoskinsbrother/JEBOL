REBOL [Title: "Opinion Survey"]
do %gui-322.r
view [
    title "Opinion Survey"
    text "Do you want programs to be easy to build?"
    panel 2 [
        label "Answer:"
        group [
            radio "Agree"
            radio "Disagree"
            radio "Not sure"
        ]
        pad
        check "I'm a programmer."
        pad
        check "I am also a REBOL expert."
        label "Name:"
        field
        label "Comment:"
        area
        pad ; temporary, for bug
    ]
    group [
        button "Submit" submit none ; the guide's http://www.rebol.net/cgi/submit.r no longer takes a post, so the answers are printed
        button "Reset"  reset
        button "Cancel" close
    ]
]
