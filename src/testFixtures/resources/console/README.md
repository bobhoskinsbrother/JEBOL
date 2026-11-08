# What `./r3-head` prints at a terminal and through pipes

The reference for goal 1c. `generate.sh` runs every case in `cases.txt` through
`./r3-head`, built here from `rebol3-source/`, and writes `transcripts.txt`.
JEBOL's `TheCommandLinesConsoleAnswersAsR3EndToEndTest` runs the same cases
through the jar and compares.

| File | What it holds |
| --- | --- |
| `cases.txt` | one case a line: name, `terminal` or `pipe`, switches, the script, then the steps |
| `pty-run.sh` | the one wrapper both sides run a command through |
| `drive.py` | runs the cases, waiting for output before typing, and writes each transcript |
| `transcripts.txt` | each case's stdout and stderr with escapes shown as bytes, its exit code, and whether the terminal was left as found |
| `a-session.typed`, `a-session.recorded` | an older fixture: one REPL session through a pipe, read by `ConsoleAsRebolPrintsItEndToEndTest` |

A step is `>text`, wait until it has been printed; `<bytes`, type them; or `.`,
end of input. Nothing sleeps: a step that is never satisfied is a `verdict:`
line in the transcript, and `generate.sh` fails when there is one.

**`pty-run.sh`** runs the command under `script` at a terminal of its own --
`script -q -f -E always -c` on Linux, `script -q /dev/null` on macOS -- with
stderr in a file of its own, since a terminal would merge it into stdout, and
`stty -g` taken inside the terminal before and after. `-E always` keeps the
terminal's echo on, as a person's terminal has it; without it `script` turns
echo off when its own stdin is a pipe. Through pipes it runs the command
directly.

## What the transcripts settle

- **At a terminal, R3 edits the line itself.** `Init_Terminal` turns off
  `ECHO`, `ICANON`, `ICRNL` and `INLCR` when the console opens, and
  `Read_Line` echoes each key and writes `\r\n` at the end of a line, which
  `ONLCR` makes `\r\r\n`. So a typed line ends `\r\r\n`, never the terminal's
  own `\r\n`. The `editor-` cases cover every key `Process_Key` handles.
- **The terminal stays in that mode for the whole run** and is restored at
  exit: `Quit_Terminal`. `line false` goes further, from the editor's mode to
  `-ixon -ixoff` with suspend and quit undefined, and `line true` comes back to
  the editor's mode, not to the terminal as found.
- **`line` writes the bracketed paste escapes under `--cgi` too,** because
  `RDM_CGI` is set after the device is opened. Bracketed paste is left on at
  exit if a script leaves `line` false.
- **`echo` writes `ESC[8m` or `ESC[28m` to wherever output goes,** a pipe
  included, and to stderr while `error` is on.
- **Through a pipe, `ask` and `input` take what one `read` gives** and strip a
  final LF: a CR is kept (`"abc^M"`), and end of input answers `""`, not none.
- **`request-password` reads to a CR or the end of input,** so a LF through a
  pipe is part of the password; ESC answers none; DEL backs over a whole UTF-8
  character. Nothing typed is shown, because the terminal is already in the
  editor's mode.
- **`read-key` through a pipe reads nothing.** The key it answers is the
  request's `modify` fields read back through a union: the code of the last
  mode set as a character, so `#"^A"` after `line` and `#"^B"` after `error`;
  after `echo` the code is 0 and the value picks a word from
  `system/catalog/event-keys`, `page-up` for true and none for false.
- **At a terminal `read-key` decodes keys** as `Read_Key_Event` does; the
  `key-` cases cover every sequence it names, its modifiers, and the ones it
  ignores.
- **Ctrl-C halts**, exit code 15, writing `[ESC]` to stderr when it lands
  inside `wait`; during `read-key` it is the key `#"^C"` and nothing halts;
  during `ask` the terminal drops what was typed and the halt comes once the
  line ends.
- **`call/console` changes nothing**: `call-console` and `call-plain` print the
  same, at a terminal and through a pipe.

## What is not recorded

- **End of input at a terminal.** `Read_Line` treats Ctrl-D as delete and a
  `read` of nothing as a reason to read again, so R3 waits for ever. JEBOL
  answers end of input there as it does through a pipe.
- **R3's own prompt.** Its REPL prints a coloured `>>` and JEBOL's REPL is its
  own; the REPL reading through this console is covered by JEBOL's tests.
- **A password past ASCII.** `request-password` makes a string of one
  character per byte, so `aéb` is four characters, and printing it writes the
  bytes back out. JEBOL has no string of bytes, and reads the password as
  UTF-8: three characters.
- **Bytes that are not UTF-8 at `ask`.** R3 reads them as binary and `input`
  fails turning them into a string. JEBOL's console hands the interpreter
  text, so they are replaced as they are read.
