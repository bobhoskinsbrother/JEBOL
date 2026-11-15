# What the easing curves answer on glibc

`easing-glibc.csv` is what the thirty curves of Siskin-framework's
Rebol-Easing 1.0.0 answer for 186 inputs, computed by the extension's own
`src/easing.c`, compiled with gcc 15.2 and run against glibc 2.43 on x86-64
Linux. `EasingFromTheSourceTest` reads it.

The inputs are 0, 0.3, the double just below 0.5, 0.5, 0.75 and 1 for every
curve, and either side of each of OUT-BOUNCE's three breakpoints, 4/11, 8/11
and 0.9. Answers are written with 17 significant digits, so each reads back as
the exact double the C produced.

## Two kinds of row

`arithmetic alone` is true for the eighteen curves built from `+ - * /` and
`sqrt`. Java computes those exactly as the C does, and the test asks for the
same double.

It is false for the twelve that call `sin`, `cos` or `pow`. Java's `Math` and
glibc round a few answers of those differently in the last bit, and the test
accepts a difference of up to two units in the last place of 1.0. Over 65,537
inputs from 0 to 1 the largest difference measured was one such unit. JEBOL does
not carry its own libm to close that gap: the libraries that would close it are
LGPL. `docs/easing-port.md` has the measurements.

## Making it again

`easing.c` and `easing.h` are not checked in. Fetch them as text from
https://github.com/Siskin-framework/Rebol-Easing at tag 1.0.0, read them, and
then:

    gcc -O2 -o table table.c easing.c -lm && ./table > easing-glibc.csv
    gcc -O2 -o every-bit every-bit.c easing.c -lm && ./every-bit > c.txt

`every-bit.c` prints the bits of every curve at 65,588 inputs, for comparing
with the same loop run over `Easing` in Java.

## Licence

Rebol-Easing is released under the Unlicense. The table is its output.
