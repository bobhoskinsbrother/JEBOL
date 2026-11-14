# The Rebol bugs JEBOL fixes, and the boundaries around them

`generate.sh` runs `probes.r3` through `./r3-head`, built here from
`rebol3-source/`, and writes `probes.txt`: one line a probe, the label, then
what it answered. An error shows as `[error id arg1]`, a port as `port`, an
image as `[image size rgb alpha]`.

The `utf` probes are the one bug goal 1m matches rather than fixes. A string
they answer shows as `[text ascii joined]`: its ASCII bytes and the four-byte
characters joined from pairs. That is what R3 and JEBOL can share, because R3
stores the invalid bytes raw and JEBOL holds U+FFFD for each (finding 249).
Two cases stay out because R3 cannot answer them: a pair that would begin
before the decode's start crashes it, and MOLD of a string ending in an
unread invalid byte hangs it.

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
