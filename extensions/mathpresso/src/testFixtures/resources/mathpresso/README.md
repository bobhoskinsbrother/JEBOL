# What MathPresso answers in its own C++

`mathpresso-answers.tsv.gz` is one table of what MathPresso compiles and
evaluates, written by `probe.cpp` linked against MathPresso commit `734c292`
and AsmJit commit `e136425`. Those are the two `master` commits on the day the
Rebol-MathPresso 0.1.0 release was built (7 February 2023), since its `.nest`
fetches both by name and not by tag. They were built with gcc 15.2 against
glibc 2.43 on x86-64 Linux. `MathPressoFromTheSourceTest` reads the table and
expects the same answers.

## The table

30,275 rows of six tab-separated columns. The first three are the input:

- **names**: the context's variable names, separated by spaces;
- **expression**: the text compiled, with `\n`, `\t` and `\\` escaped;
- **data in**: the doubles handed to `evaluate`, as sixteen hex digits each.

The last three are what came back:

- **result**: the double answered, as hex, or `none` if the text did not compile;
- **data out**: the doubles after evaluation, which is where assignments land;
- **messages**: what MathPresso logged, each as the extension prints it, `ERROR: `
  or `WARNING: ` then the message, joined with `\n`.

The probe compiles as the extension does: `addBuiltIns`, the names added in
order at eight bytes apart, `kOptionVerbose`, and a log. Every row is compiled
in a fresh context.

The test reads it gzipped, then checks the decompressed table's SHA-256,
`43662e06...6c13d`, and its row count, before reading any row.

## Making it again

`corpus.py` writes the inputs, in five groups: statements and scopes, number
literals, every unary operator, every binary operator and function, and calls
into the maths library. The operands are a grid of special doubles: both zeros,
both infinities, quiet and signalling NaNs of each sign and payload, the
smallest subnormal, the largest double, halves, 2^52 + 1 and others.

MathPresso and AsmJit are not checked in. Fetch both as source from
https://github.com/kobalicek/mathpresso and https://github.com/asmjit/asmjit,
check out the two commits above side by side, read them, and then:

    cmake mathpresso -B build -DMATHPRESSO_STATIC=1 -DMATHPRESSO_TEST=0 \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    cmake --build build
    g++ -O2 -std=c++17 -DMATHPRESSO_STATIC -Imathpresso/src -o probe probe.cpp \
        build/libmathpresso.a -lpthread -lrt
    python3 corpus.py corpus > in.tsv
    ./probe < in.tsv > out.tsv
    python3 corpus.py join in.tsv out.tsv | gzip -9 -n > mathpresso-answers.tsv.gz

The probe must link the library as the extension does: from the archive, after
its own object, and referring to nothing in AsmJit itself. The linker then takes
`mpcompiler.cpp.o` from the archive before `cpuinfo.cpp.o`, so MathPresso's
global runtime is constructed first and AsmJit's CPU table is wiped after it,
and the JIT rounds with SSE2 (`docs/mathpresso-port.md`). Link it any other way
and check `ceil(x)` of a tiny negative number: SSE2 answers +0.

## Licence

MathPresso is under the zlib License, Copyright (c) 2015 Petr Kobalicek; the
table is its output. `probe.cpp` and `corpus.py` are JEBOL's.
