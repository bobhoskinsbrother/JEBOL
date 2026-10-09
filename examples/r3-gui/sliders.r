REBOL [Title: "Sliders"]
do %gui-322.r
view [
    title "Progress, Scrollers, and Sliders"
    text "These will change:"
    panel [
        prog: progress
        sbar: scroller attach 'prog
    ]
    text "Change the above, variable amounts::"
    panel 80.200.180.80 [
        slider   attach 'sbar
        scroller attach 'sbar
    ]
    text "Change the above, predefined amounts:"
    group 3 [
        button "Set 0%"   set 'sbar 0%
        button "Set 10%"  set 'sbar 10%
        button "Set 50%"  set 'sbar 50%
        button "Set 90%"  set 'sbar 90%
        button "Set 100%" set 'sbar 100%
        button "Set 150%" set 'sbar 150%
    ]
    text "How wide to make them (their deltas):"
    panel 3 200.100.80.80 [
        radio "Delta 10%" on set 'sbar 'delta 10%
        radio "Delta 50%"  set 'sbar 'delta 50%
        radio "Delta 100%" set 'sbar 'delta 100%
    ]
]
