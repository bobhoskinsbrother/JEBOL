# The Rebol bugs JEBOL fixes, and the boundaries around them

`generate.sh` runs `probes.r3` through `./r3-head`, built here from
`rebol3-source/`, and writes `probes.txt`: one line a probe, the label, then
what it answered. An error shows as `[error id arg1]`, a port as `port`, an
image as `[image size rgb alpha]`.

`generate-fixed.sh` builds a second R3 from a scratch copy of `rebol3-source/`
with `fixes.patch` applied -- the clip in `image_diff`, the step past the block
and the pointer to the bad value in `Create_Image`, and `0xFF` for `0x7F` in
`Find_Str_Char`'s byte arm -- runs the same probes through it, and writes
`probes-fixed.txt`. It takes about half a minute and leaves `rebol3-source/`
and `./r3-head` alone. The clipboard and MIDI are not patched: the build has no
MIDI and its Linux clipboard refuses everything after the first OPEN.

**Both scripts copy `probes.r3` into an empty folder before running it.** R3
changes into the folder that holds a script before it runs it, so a script run
from where it sits writes its files beside itself, and the next run finds them.
The first transcript was made that way; `gone.txt` and `renamed.txt` were the
files it left here, and `file there after rename` answered differently on its
first run than on every run after. Finding 246.

`TheRebolBugsTranscriptFromTheSourceTest` reads all three files. Every line
JEBOL answers equals `probes.txt`, except the lines decision 71 fixes on
purpose; each of those must differ from `probes.txt` and equal
`probes-fixed.txt`. Regenerate both transcripts after changing `probes.r3`.
