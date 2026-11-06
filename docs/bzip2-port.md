# The bzip2 port

Rebol-Bzip2 1.1.0, wrapping libbzip2. JEBOL serves it in Java as decision 58
says. This note carries what the code cannot. The codec is in
`org.jebol.domain.eval.compression.bzip2`, and the module, its seven commands and
its two handles are `Bzip2Extension` and `Bzip2Stream`. The tables in
`src/testFixtures/resources/bzip2/` hold the C's own answers, and its README lists
each boundary with what covers it.

---

## Which bzip2

**R3's address does not work.** `system/modules/bzip2` names
`releases/download/1.1.0/`, which answers 404: the release's tag is `v1.1.0`. A
real 3.22.5 cannot fetch this module from the address it names. JEBOL keeps no
address, so nothing here depends on it.

**The nest does not say which repository.** `github: @bzip2` resolves to no GitHub
repository we could find. It builds with CMake and `ENABLE_LIB_ONLY`, which release
1.0.8 does not have, so it is the unreleased 1.1.0 line. GitLab's upstream at
`66c46b8` and libarchive's mirror hold identical codec files, and both differ from
1.0.8 only in whitespace, comments, an error message and a `fopen` mode. So the
octets are 1.0.8's, and `version` answers `"1.1.0"`, which CMake writes.

The extension's `Date:` is read from its checked-in generated header. The release
regenerates that header when it builds, so its date is a few days later.

## What is ported, and what is not

The whole of `blocksort.c`, `compress.c`, `decompress.c` and `huffman.c`, and the
stream half of `bzlib.c`. Not ported, because the extension cannot reach them: the
`FILE*` API, small mode, verbosity, and a work factor other than 30. `AssertH` is
not ported either: each guards an invariant no input can break, and in the C it
ends the process.

## Where the code keeps the C's shape

`Bzip2BlockSort` and `Bzip2DecompressionStream` say not to tidy them, and this is why.

**Which sort runs decides the octets.** Rotations of a periodic block tie, so
where the main sort gives up and the fallback takes over changes `origPtr`. The
budget is spent exactly where the C spends it, per eight compares in
`mainGtU`, and tested after every third insertion. The fallback uses the same
`ftab` as its bit table and clears only `2 + nblock/32` words of it, as the C does.

**The block, the pointers and the output do not share memory in the Java.** The C
overlays `mtfv` on `ptr`, `quadrant` and `zbits` on `block`, and the fallback's
`eclass` on `block`, which it then rebuilds. No answer depends on that overlay,
so the Java keeps separate arrays and has nothing to rebuild.

**A state is the C's label, and an error resumes from the last one reached.**
`Bzip2Stream` can call the decoder again after an error, and the C then reads on
from the `GET_BITS` it last passed, with its counters as they stood. So a loop's
counter is advanced after its body, and the state changes only on reaching the next
label: a re-entry at `MAPPING_1` with `i` at 16 reads one more bit, as the C's
`case` inside its `for` does.

**The fast run copier returns corruption without saving its locals.** The output
of that call is lost, and the next call starts from where the last save left it.

## Where JEBOL differs

There is no C answer to agree with in any of these.

- **`write/flush` raises `"Bzip2 compression failed."`.** The C's loop breaks only
  on `BZ_FLUSH_OK`, which `BZ2_bzCompress` answers only when the output did not
  fit. A flush that fits answers `BZ_RUN_OK`, and the loop calls again for ever, not
  answering SIGTERM (`module-flush.txt`). The extension's own CI never calls it. A
  flush that does not fit answers with what fitted, as in C.
- **A selector error, then another call, is a data error.** `j` carries on past
  `nGroups`, and the C runs `pos[v]` off the end of a stack array. Found because the
  tables differed between `-O0` and `-O2`.
- **A symbol past the move table is a data error.** A stale `perm` entry can make
  `nn` 256, which indexes past `mtfbase` in the C.
- **Re-entering the mapping loops past their end writes nothing stray.** The C
  writes `inUse16[16]`, and `inUse[272]` and beyond, into the fields that follow.
  Those fields are recomputed before use. UBSan names these, and an `origPtr` shift
  that overflows, which Java wraps the same way.
- **No crash after collection.** With collection on, a run through several
  encoder handles segfaulted in `handle_compress` under `cmd_write`, and did not
  with `recycle/off`. We did not find the cause. JEBOL's handles share no memory.

## What the extension does

- `compress` reads level 0 as 9 and clamps the rest to 1 to 9, reading a negative
  as 1. `make-encoder` only clamps, so its level 0 is 1. Both default to 6.
  COMPRESS with `'bzip2` and no `/level` passes R3's unknown level, which the C
  reads as -1, so it writes `BZh1`.
- One-shot `decompress` starts with room for four times the input, or the limit,
  and quadruples on `BZ_OUTBUFF_FULL`. No octets ask for no room, so `#{}` answers
  `#{}` without reaching the decoder. The room is `SERIES_REST`, so what it holds
  past the asked size can decide between an answer and an error.
- `/size` keeps what fitted, so it can answer from a stream damaged further on.
- `/max` is a fourth refinement `goals.md` did not list. `/max 0` means no cap.
- `read` flushes a block on an encoder, so it ends a block where a script reads,
  and it hands back what fitted in the series' room: a block bigger than that
  splits across reads at exactly the room's end (`module-chunks.txt`).
- An empty write then a read gives `#{425A68}`: three octets of the header, the
  fourth still in the bit buffer.
- A handle's mold is `#(handle! bzip2-encoder bzip2:enc:0x...)`. JEBOL's address is
  the object's identity hash, which is no address but is as stable and as opaque.

## Strings

Finding this port's module tests fail on `"ěšč"` found a fault in every bundled
extension: see "Strings reach C as UTF-8" in `docs/rebol-findings.md`.
