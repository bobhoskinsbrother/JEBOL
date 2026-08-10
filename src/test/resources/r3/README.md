# R3's own library surface, as the binary reports it

`surface.txt` is every function in a real R3 3.22.1's
`system/contexts/lib`, one per line, with its arguments and refinements.
It is what `SurfaceReportTest` compares JEBOL against, and it is the
porting work list.

Taken with:

```rebol
foreach w sort words-of system/contexts/lib [
    set/any 'v try [get/any in system/contexts/lib w]
    if all [not error? :v  any-function? :v] [
        line: copy ""
        repend line [form w " |"]
        foreach item spec-of :v [
            case [
                refinement? :item [repend line [" /" form to word! item]]
                any-word? :item   [repend line [" " form to word! item]]
                block? :item      [repend line ["<" form item ">"]]
            ]
        ]
        print line
    ]
]
```

Checked in rather than taken fresh each run, so the list is the same on a
machine with no `./r3` and so a change to it shows up in a diff. Retake it
when the reference binary changes, and expect the retaking to be a
reviewable commit of its own.
