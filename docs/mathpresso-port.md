# The MathPresso port

Rebol-MathPresso 0.1.0, the extension R3's `system/modules` names at
`https://github.com/Siskin-framework/Rebol-MathPresso/releases/download/0.1.0/`,
wrapping Petr Kobalicek's MathPresso. MathPresso parses an expression and
compiles it to x86 machine code with AsmJit. JEBOL serves the extension in Java
as decision 58 says, and decision 60 says how: it interprets what the JIT would
emit, instruction by instruction, so it answers the doubles the Linux release
answers. This note carries what the code cannot.

---

## Which MathPresso

**The release does not pin MathPresso or AsmJit.** Its `Rebol-MathPresso.nest`
names `github: @asmjit` and `github: @mathpresso`, and the Siskin builder
fetches each repository's `master` when it builds. The release's assets were
uploaded on 7 February 2023, so the port follows the last commits before that:
MathPresso `734c292` (8 January 2023) and AsmJit `e136425` (18 January 2023).
MathPresso changed nothing that matters before October 2025, when it moved to
AsmJit's UJIT.

---

## What the extension is

**Three commands and no exports.** `context spec [block!]`, `compile context
[handle!] expression [string!]` and `eval expression [handle!] variables
[vector!]`. The header has no `Exports:`, so lib's own CONTEXT stays, and the
module's script is `protect/hide 'words`, which hides nothing that exists.

**CONTEXT takes words and nothing else.** It walks the block from its index and
adds each `word!`, skipping every other value without counting it. The name is
`RL_WORD_STRING` of the word's canon, so it is the first spelling R3 interned:
`m/context [X]` names the variable `x`, because `x` is a boot symbol, and `X`
then does not resolve. MathPresso refuses a name it already holds (a repeated
word, `NaN`, `sin`), but the extension counts the word anyway, so the vector
still needs a double for it.

**COMPILE reads the string from its head, as a C string.** `SERIES_TEXT(src)` is
the series' first byte whatever its index, and `strlen` stops at a zero. A wide
string is encoded to UTF-8 first. MathPresso refuses any byte past ASCII, so
the width never changes the answer. A handle that is not an MPContext answers
false. A refusal answers none. The extension compiles with `kOptionVerbose` and
prints every message MathPresso logs, warnings included, as `ERROR: ` or
`WARNING: ` and the text, without the line and column MathPresso gives it.

**EVAL checks three things, in this order:** that the handle is an
MPExpression, that the vector holds doubles (`VECT_TYPE == 11`, so `float32!`
is refused too), and that there are as many doubles from the vector's index as
the context counted words. Each failure is `command-fail` carrying the C's
message. Then it runs the code on the vector's memory, from its index, writes
back every global variable the expression assigned, and answers the result as a
decimal. It writes into a protected vector, because the C does not ask.

---

## What the JIT does that the port has to do too

**A constant expression is folded by the optimizer, in C, and anything else by
the JIT, in SSE2.** The two disagree, and the port keeps both:

| Expression    | Folded                   | Run                                                   |
|---------------|--------------------------|-------------------------------------------------------|
| `isfinite`    | 0 for an infinity        | 1, because `-\|x\| <= 0` holds for minus infinity     |
| `abs(-0)`     | +0, from `fabs`          | -0, from `0 - x` and then `maxsd`                     |
| `-0`          | -0, a sign flip          | +0, from `0 - x`                                      |
| `x > y`, `>=` | false for a NaN          | true, because `cmpsd` uses NLE and NLT                |
| `x % y`       | `fmod`, by x87 `fprem`   | `x - trunc(x / y) * y`                                |
| `min`, `max`  | `a` if `a` is NaN        | `minsd`'s second operand if either is NaN             |

**`+`, `*`, `avg`, `min` and `max` swap their operands** when the left one is a
read-only variable and the right one is not. That only shows with `min` and
`max` on a NaN, or in the payload of two NaNs added: `min(x, NaN)` is `x`,
because `x` is read only and moves to the right.

**A variable with itself is one register.** `x op x` loads `x` once into a
register both operands share. Most instructions read both before they write, but
two do not: `copysign(x, x)` masks the register twice and answers +0, and
`x % x` overwrites the divisor with the quotient, so it answers `x - 1`.

**The JIT rounds with SSE2, never SSE4.1's `roundsd`, on the Linux release.**
MathPresso asks `CpuInfo::host()` for SSE4.1 when it compiles. But its global
`JitRuntime` is constructed at load time, and its constructor fills AsmJit's
cached CPU information, which is a static object in `cpuinfo.cpp`. That object
is constructed afterwards, because the linker took `mpcompiler.cpp.o` out of the
archive first, and its no-init constructor still zeroes the features. So every
feature reads as absent and every compiled expression rounds with the shifter
trick: add `2^52 + 2^51`, take it away, and correct. The extension links the
same archive behind an object that only names MathPresso, as the probe does, so
the release gets the same order. That is visible:

- `ceil(x)` of a tiny negative number is +0, where `roundsd` answers -0;
- a signalling NaN passes `floor`, `ceil`, `trunc`, `round` and `roundeven`
  unquieted;
- `frac(-0)` is -0, because the floor it takes away is +0.

The folded path calls glibc's `floor`, `ceil`, `trunc` and `rint`, which do use
`roundsd`, so a constant still rounds the IEEE way.

**NaNs are carried as bits.** The interpreter holds every double as its `long`
bits, so a payload, a sign and a signalling NaN survive as they do in a
register, the web included, where JavaScript may rewrite a NaN. Arithmetic
follows SSE's rules: the first operand's NaN quieted, else the second's, else
the default NaN, which is negative. The folded `%` is x87 `fprem` (gcc inlines
`fmod` that way and calls the library only to set `errno`), and of two NaNs
x87 keeps the one with the larger significand and, of equal ones, the positive.

---

## The compiler's own faults, kept

**An assignment from a non-constant leaves a stale constant.** The optimizer
records `x = 5` as "x is 5" and folds every later read of `x`, and `x = y` does
not clear it. So `x = 5; x = y; x` answers 5, while the vector gets `y`.

**An exponent of sixteen reads past the table of powers of ten.** The fast path
takes exponents from -16 to 16, but `mpPow10Table` holds 1e0 to 1e15. In this
build the eight bytes after it are the first eight entries of `mpCharClass`,
each 65, so `1e16` is `0x4141414141414141` and `1e-16` is one over it. The
Linux release builds the library with gcc from the same source, so it reads the
same bytes. A build that orders the two tables differently would read others.

**`+1` does not compile.** A unary plus on a constant reaches the optimizer's
fold, which has no case for it: "Invalid unary operation '<none>'." `+x` is
fine.

**A block's last statement needs a semicolon.** `{1}` is "Unexpected token."
at the `}`, and `{1;}` compiles and answers NaN, because the optimizer drops a
constant statement from a nested block.

---

## The maths library

**MathPresso calls the C library for `exp`, `log`, `log2`, `log10`, the
trigonometric and hyperbolic functions, `pow`, `atan2` and `hypot`.** So the
release's answer is the platform's: glibc on Linux, Microsoft's CRT on Windows,
Apple's libm on macOS, and the three differ in the last bit.

**JEBOL answers Java's, with glibc's NaNs, and the last bit is not a goal.**
The release itself answers differently in the last bit on each platform, and
Java's `Math` differs between JVMs, CPUs and the web build, so the tests ask
for a library answer within 1e-12 of glibc's (relative), never for its bits.
`log2` is `log(x) / log(2)`, except at a power of two, where it is the exponent
exactly. Which of `Math` and `StrictMath` each function calls is incidental.

**What is exact is every special value.** For each function the port answers
glibc's bits where the answer is a NaN or a limit, found from a grid of special
operands through every function:

- a NaN argument comes back quieted, its payload and sign kept; `atan2` takes
  its second argument's NaN first;
- outside its domain, each function answers its own NaN: `asin`, `acos` and
  `log10` the positive one, `sin`, `cos`, `tan`, `log`, `log2`, `sqrt`, `pow`
  and `fmod` the negative one;
- `pow` follows C99 where Java does not: `pow(1, NaN)` and `pow(-1, INF)` are
  1, a signalling NaN to the power 0 is a NaN, and a NaN to an odd integer
  power loses its sign;
- `hypot` with an infinity is infinity even beside a quiet NaN, but not beside
  a signalling one.

---

## How the port was checked

MathPresso and AsmJit were fetched as source, read, and built in a scratch
directory with cmake, with a probe of JEBOL's that compiles and evaluates as the
extension does. No release binary was fetched or run.

A corpus of 95,000 expressions was run through the probe and through the port:
every unary and binary operator and function over a grid of 38 special
operands, each in every form that reaches a different path (a variable, a
computed value, a variable with itself, a variable assigned first so it is read
only, constants the optimizer folds), 3,000 number literals, 140 programs of
statements and scopes, and 11,000 calls into the maths library. All agreed,
messages included, apart from the last bit of the maths library's functions.
`src/testFixtures/resources/mathpresso/` keeps 30,275 of them and says how to
make them again; rows that call the maths library are compared closely, the
rest bit for bit. `TheMathPressoModuleFromTheSourceTest` runs the extension's
own CI script and compares all 62 answers with the probe's to within 1e-9.

The SSE2 rounding was found by the corpus, not by reading: `ceil` gave a
signalling NaN back unquieted, which `roundsd` would not. The probe's machine
code (`kOptionDebugMachineCode`) showed the shifter sequence, and a probe of
AsmJit's CPU table, run inside a binary that links MathPresso, showed every
feature zero.

## Licence

Rebol-MathPresso declares the Apache License 2.0 in its module header.
MathPresso is under the zlib License, Copyright (c) 2015 Petr Kobalicek. The
files written from it stay under the zlib License and NOTICE says which they
are. `LibmCalls` is JEBOL's own, from measuring glibc's answers, not from its
source.
