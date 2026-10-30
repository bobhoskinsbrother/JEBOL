# The easing port

Siskin-framework's Rebol-Easing 1.0.0, the extension R3's `system/modules`
names at `https://github.com/Siskin-framework/Rebol-Easing/releases/download/1.0.0/`.
JEBOL serves it in Java: `import 'easing` answers the module from inside the
build, and nothing is ever fetched (decision 58). This note carries what the
code cannot: what the extension is, how the port was checked, and the one place
it does not agree to the bit.

---

## What the extension is

**Thirty curves and one function.** `src/easing-rebol-extension.r3` declares
the commands, each `x [decimal!]` with a line of help, and appends `tween` to
the module's REBOL. `src/easing.c` is the arithmetic, from Warren Moore's
AHEasing. `src/easing-commands.c` reads the argument as a 64-bit decimal,
calls one function, and writes the answer back over it.

**The C has a thirty-first curve the module does not.** `QuinticEaseInOut` and
`cmd_in_out_quint` are there, but the command list in the `.r3` file has no
`in-out-quint`, and the dispatch table is generated from that list, so no
script can reach it. JEBOL's module has no `in-out-quint` either.

**Only `tween` is exported.** The header is
`REBOL [Title: {Rebol Easing Extension} Type: module Exports: [tween]]`, so
after `import 'easing` the curves are reached through the module
(`m/in-quad 0.5`, or `tween ... 'in-quad`) and `:in-quad` in a script is unset.
JEBOL carries the header as it is. The header has no `Name:`; R3 names a
downloaded extension through LOAD-MODULE's `/as`, and JEBOL's delayed entry
names it the same way when the interpreter is built.

**The curves do not check their range.** The `.r3` file says "no validation!":
`in-quad 2.0` is `4.0` and `in-quad -1.0` is `1.0`.

**The width of the arithmetic depends on the platform.** `easing.h` uses
`double` when `__LP64__` is defined and `float` otherwise, so the 64-bit Linux
and macOS builds compute in double and a 64-bit Windows build, where `long` is
32 bits and `__LP64__` is not defined, computes in float and widens the answer.
JEBOL computes in double, as Linux and macOS do.

---

## How the port was checked

The extension's `easing.c` was fetched as text, read, and compiled in a
scratch directory beside a `main` that prints the bits of every curve's answer.
Nothing else from the extension was built, and no release binary was run.
`src/testFixtures/resources/easing/` holds both generators and the README says
how to make the table again.

Over 65,588 inputs per curve -- every multiple of 1/65536 from 0 to 1, and 0, 1,
0.5, 4/11, 8/11, 0.9, 0.25, 0.75, 0.1, 0.2, 0.3, -1, 2, -0.5, 1.5 and 1e-300
with the double either side of each -- compared against gcc 15.2 and glibc 2.43
on x86-64:

| Curves | Built from | Inputs that differ |
| --- | --- | --- |
| the other eighteen | `+ - * /` and `sqrt` | none |
| in-sine, out-sine, in-out-sine | `sin`, `cos` | 81, 128, 73 |
| in-expo, out-expo, in-out-expo | `pow` | 65, 17, 31 |
| in-elastic, out-elastic, in-out-elastic | `sin` and `pow` | 182, 19, 88 |
| in-back, out-back, in-out-back | `sin` | 85, 24, 50 |

Every difference is at most 2.22e-16 -- one unit in the last place of 1.0 --
and comes from the maths library, not the port: Java's `Math.sin`, `Math.cos`
and `Math.pow` round a few answers differently from glibc's. On the raw functions
over 200,000 arguments `Math` disagrees with glibc on 0.03% to 0.2%, and
`StrictMath` on 3% to 9%, which is why the port uses `Math`, as JEBOL's own
`sine`, `cosine`, `exp` and `log` do.

**JEBOL does not close that gap, on purpose.** Matching glibc to the bit would
mean carrying glibc's `sin`, `cos` and `pow`, which are LGPL, and JEBOL takes no
LGPL code. A macOS r3 uses Apple's libm and a Windows one computes in float, so
no single answer is "the" extension's anyway. `EasingFromTheSourceTest` asks for
the same double on the eighteen arithmetic curves and accepts two units in the
last place of 1.0 on the twelve that call the library.

The answers a script sees were read off a real 3.22.5 with the extension
loaded: `[m/in-quad 0.5 m/out-elastic 0.3 m/in-out-bounce 0.7 m/linear 0.25]` is
`[0.25 0.876538957425608 0.946 0.25]`, and so is JEBOL's. The module's words,
in their order, and `:in-quad` being unset outside it came from the same run.
`tween` is REBOL, and its answers were read off `./r3-head` with stand-in curves.

## Licence

Rebol-Easing, its C and its REBOL, are released under the Unlicense.
`Easing.java` and the module's specs and `tween` in `EasingExtension.java` are
written from them and stay under it; NOTICE says so.
