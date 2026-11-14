# The five Rebol bugs, and the boundaries around them

`generate.sh` runs `probes.r3` through `./r3-head`, built here from
`rebol3-source/`, in an empty folder of its own, and writes `probes.txt`: one
line a probe, the label, then what it answered. An error shows as
`[error id arg1]`, a port as `port`, an image as `[image size rgb alpha]`.

This transcript is what a real 3.22.5 answers, bugs included. Goal 1k reads it
three ways:

- **Where JEBOL already agrees**, it must keep agreeing: the boundaries either
  side of each bug (`find` 127 and 256, a `make image!` fill, `image-diff/part`
  inside the image, every GCM shape, `delete` on a closed port).
- **Where JEBOL differs and R3 is right**, JEBOL is fixed to match: the CCM
  shapes, APPEND/CHANGE of a block of tuples, the file and directory ports.
- **Where R3 is wrong on purpose** (decision 71), the transcript keeps R3's
  answer as the record, and the JEBOL test names the line and spells out the
  fixed answer beside it.

Measured on 2026-11-14 against the jar at `93344d9e`: 29 of 122 lines differ,
the list in `goals.md` goal 1k.

`file there after rename` answered `[file _]` on the first run on 2026-11-14 and
`[file file]` on four runs after it, with the probes before it changed only in
the `find` section. Goal 1k says to find out why before the line is relied on.
