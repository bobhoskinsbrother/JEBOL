import random
import struct
import sys

SPECIAL_BITS = [
    "0000000000000000", "8000000000000000", "3ff0000000000000", "bff0000000000000",
    "3fe0000000000000", "bfe0000000000000", "3ff8000000000000", "bff8000000000000",
    "4004000000000000", "c004000000000000", "4008000000000000", "4376345785d8a000",
    "c376345785d8a000", "7e37e43c8800759c", "4330000000000001", "c330000000000001",
    "3fdfffffffffffff", "bfdfffffffffffff", "7ff0000000000000", "fff0000000000000",
    "7ff8000000000000", "fff8000000000000", "7ff8000000000123", "fff8000000000123",
    "7ff0000000000123", "fff0000000000123", "0000000000000001", "8000000000000001",
    "7fefffffffffffff", "ffefffffffffffff", "4086800000000000", "c086800000000000",
    "3ff0000000000001", "4000000000000000", "c000000000000000", "3fb999999999999a",
    "4433000000000001", "43300000000003e8",
]

GRID_BITS = [
    "0000000000000000", "8000000000000000", "3ff0000000000000", "bff0000000000000",
    "3fe0000000000000", "bff8000000000000", "4008000000000000", "c376345785d8a000",
    "4330000000000001", "bfdfffffffffffff", "7ff0000000000000", "fff0000000000000",
    "7ff8000000000000", "fff8000000000000", "7ff8000000000123", "fff0000000000123",
    "8000000000000001", "7fefffffffffffff", "c000000000000000", "4086800000000000",
]

UNARY = ["isnan", "isinf", "isfinite", "signbit", "round", "roundeven", "trunc", "floor", "ceil", "abs",
         "exp", "log", "log2", "log10", "sqrt", "frac", "recip", "sin", "cos", "tan", "sinh", "cosh", "tanh",
         "asin", "acos", "atan"]
BINARY_FUNCTIONS = ["avg", "min", "max", "pow", "atan2", "hypot", "copysign"]
BINARY_OPERATORS = ["==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/", "%"]

LITERALS = ["0", "-0", "1", "-1", "0.5", "-0.5", "1.5", "-2.5", "1e17", "NaN", "-NaN", "INF", "-INF",
            "0/0", "-(0/0)", "4503599627370497", "0.49999999999999994", "1e-320", "720", "PI"]

NEGATIVE_ZERO = "8000000000000000"

rows = []


def hexof(value):
    return struct.pack('>d', value).hex()


def row(names, expression, values):
    escaped = expression.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    rows.append(names + "\t" + escaped + "\t" + " ".join(values))


def call(op, left, right):
    if op in BINARY_FUNCTIONS:
        return op + "(" + left + ", " + right + ")"
    return left + " " + op + " " + right


def unary_cases():
    for f in UNARY:
        for x in SPECIAL_BITS:
            row("x", f + "(x)", [x])
            row("x y", f + "(x + y)", [x, NEGATIVE_ZERO])
            row("x", "x = " + f + "(x)", [x])
        for literal in LITERALS:
            row("x", f + "(" + literal + ")", ["0000000000000000"])
    for x in SPECIAL_BITS:
        for program in ["-x", "!x", "+x", "-(-x)"]:
            row("x", program, [x])
        row("x y", "-(x + y)", [x, NEGATIVE_ZERO])
    for literal in LITERALS:
        row("x", "!(" + literal + ")", ["0000000000000000"])
        row("x", "-(" + literal + ")", ["0000000000000000"])


def binary_cases():
    for op in BINARY_OPERATORS + BINARY_FUNCTIONS:
        for x in GRID_BITS:
            for y in GRID_BITS:
                row("x y", call(op, "x", "y"), [x, y])
        for x in SPECIAL_BITS:
            row("x", call(op, "x", "x"), [x])
        for x in GRID_BITS[::3]:
            for y in GRID_BITS[::2]:
                row("x y z", call(op, "(x + z)", "y"), [x, y, NEGATIVE_ZERO])
                row("x y z", call(op, "x", "(y + z)"), [x, y, NEGATIVE_ZERO])
                row("x y", "x = " + call(op, "x", "y"), [x, y])
                row("x y", "var a = x; " + call(op, "a", "y"), [x, y])
                row("x y", "y = y; " + call(op, "y", "x"), [x, y])
        for left in LITERALS:
            for right in LITERALS[::2]:
                row("x", call(op, "(" + left + ")", "(" + right + ")"), ["0000000000000000"])
            for y in GRID_BITS[::5]:
                row("y", call(op, "(" + left + ")", "y"), [y])
                row("y", call(op, "y", "(" + left + ")"), [y])


def literal_cases():
    random.seed(7)
    fixed = ["1e15", "1e16", "1e17", "1e-15", "1e-16", "1e-17", "10e15", "9e15", "1.5e16", "123456789012345",
             "1234567890123456", "12345678901234567", "0.000000000000001", "0.0000000000000001",
             "100000000000000000000", "1e0000000", "1e00000001", "1e0000000001", "1e4294967296", "1e4294967297",
             "1e999999", "1e1000000", "1e-999999", "0.1", "0.2", "0.3", "1.1", "2.675", "9007199254740993",
             "5.", ".5", "0.", "00.00", "0e0", "1E5", "1e+5", "1e-5", "1.e5", "123.456e-7", "1e", "1e+", "1e-",
             "1e+a", "0x10", "1_", "1.5.3", ".", "..5", "1..5", "99999999999999999999999999",
             "0.30000000000000004", "4.35", "1.7976931348623157e308", "1.7976931348623159e308",
             "2.2250738585072014e-308", "4.9e-324", "2.4e-324", "000123", "1000000000000000.5",
             "123456.789012345", "1234567.89012345678"]
    for text in fixed:
        row("x", text, ["0000000000000000"])
    for _ in range(1500):
        whole = "".join(random.choice("0123456789") for _ in range(random.randint(0, 20)))
        fraction = "".join(random.choice("0123456789") for _ in range(random.randint(0, 20)))
        text = whole
        if random.random() < 0.7:
            text += "." + fraction
        if not text or text == ".":
            text = "0" + text
        if random.random() < 0.6:
            text += random.choice("eE") + random.choice(["", "+", "-"]) + str(random.randint(0, 40))
        row("x", text, ["0000000000000000"])


STATEMENTS = [
    ("x", "x"), ("x", ""), ("x", "   "), ("x", "// only a comment"), ("x", "x // c\n + 1"),
    ("x", "1; 2"), ("x", ";;;"), ("x", ";1"), ("x", "{}"), ("x", "{1}"), ("x", "{1;}"), ("x", "{x = 2;}"),
    ("x", "{ x = 2; } x"), ("x", "{ x = 2; }; x"), ("x", "var a"), ("x", "var a; a"), ("x", "var a = 1; a"),
    ("x", "var a = 1, b = 2; a + b"), ("x", "var a = 1, b; b"), ("x", "var a = a"), ("x", "var"),
    ("x", "var 1"), ("x", "var a, 1"), ("x", "var a = 1 b"), ("x", "var x = 2; x"), ("x", "x; var x = 2; x"),
    ("x", "x = 1; var x = 2"), ("x", "var sin = 1"), ("x", "var PI = 1"), ("x", "var NaN = 1"),
    ("x", "{ var a = 1; { var a = 2; } }"), ("x", "var a = 1;\n { var a = 2; a }"),
    ("x", "var a = 1;\n\n  { var a = 2; a; }"), ("x", "{ var a = 1; } a"), ("x", "{ var x = 5; } x"),
    ("x", "{ var x = 5; x = 6; } x"), ("x", "x = 5; x = 6"), ("x y", "x = 5; x = y; x"), ("x y", "x = y; x = 5; x"),
    ("x y", "x = y = 3"), ("x y", "x = y = x + 1"), ("x", "x = 1 = 2"), ("x", "PI = 3"), ("x", "-x = 3"),
    ("x", "(x) = 3"), ("x", "((x)) = 3"), ("x", "x + (x = 3)"), ("x", "sin(x = 1)"), ("x", "x = -x"),
    ("x", "x = x = 1"), ("x", "2 - 3 - 4"), ("x", "x - 3 - 4"), ("x", "2 * 3 + 4 * 5 < 27 == 1"),
    ("x", "1 < 2 < 3"), ("x", "3 > 2 > 1"), ("x", "1 == 1 == 1"), ("x", "-2 * -x"), ("x", "!!x"), ("x", "--x"),
    ("x", "x++"), ("x", "x += 1"), ("x", "x ? 1 : 2"), ("x", "x & 1"), ("x", "x | 1"), ("x", "x ^ 1"),
    ("x", "~x"), ("x", "x >> 1"), ("x", "x >>= 1"), ("x", "x >>> 1"), ("x", "x << 1"), ("x", "x && 1"),
    ("x", "x[1]"), ("x", "x @ 1"), ("x", "1a"), ("x", "sin(1, 2)"), ("x", "sin()"), ("x", "pow(1)"),
    ("x", "pow(1, 2, 3)"), ("x", "sin"), ("x", "sin x"), ("x", "sin(1"), ("x", "sin(1;"), ("x", "(1"),
    ("x", "(1;"), ("x", "1)"), ("x", "foo"), ("x", "foo(1)"), ("x", "x x"), ("x", "x;x"), ("x", "x,x"),
    ("x", "x:x"), ("x", ",x"), ("x", "x +"), ("x", "x + ;"), ("x", "x + )"), ("x", "x\x01"), ("x", "x\x7f"),
    ("x", "\"x\""), ("x", "'x'"), ("x", "#x"), ("x", "$x"), ("x", "x\\y"), ("x", "x`"),
    ("x", "var\tq = 3;\nq * 2"), ("x", "\x0bx\x0c"), ("x", "_ab"), ("_ab", "_ab * 2"), ("X", "X"), ("X", "x"),
    ("var", "var"), ("var x", "x"), ("x x", "x + 1"), ("x NaN y", "y"), ("x sin y", "y"), ("a1 b2", "a1 + b2"),
    ("x", "1 +\n\n\n foo"), ("x", "+1"), ("x", "+(1 + 2)"), ("x", "+x + 1"), ("x", "-+-1"), ("x", "-+-x"),
    ("x", "!-x"), ("x", "x * 1"), ("x", "1 * x"), ("x", "x / 1"), ("x", "1 / x"), ("x", "x + 0"),
    ("x", "0 + x"), ("x", "x - 0"), ("x", "0 - x"), ("x", "pow(x, 1)"), ("x", "pow(1, x)"),
    ("x y", "x = 2; y = x * 3; x + y"), ("x y", "var t = x; x = y; y = t; x - y"),
    ("x y step amplitude result", "y=sin(x); x=x+step; result=round(y*amplitude)/100"),
    ("x y step amplitude result", "y=sin(x)+cos(x/2);\n x=x+step;\n result=round(y*amplitude)/100"),
    ("a b c d e f g h", "a + b * c - d / e % f + g * h"), ("a b", "a = b; b = a; a + b"),
]

STATEMENT_DATA = ["3ff0000000000000", "4000000000000000", "4008000000000000", "4010000000000000",
                  "4014000000000000", "4018000000000000", "401c000000000000", "4020000000000000"]


def statement_cases():
    for names, program in STATEMENTS:
        row(names, program, STATEMENT_DATA[:max(1, len(names.split()))])


def library_cases():
    random.seed(11)
    for f in ["exp", "log", "log10", "sin", "cos", "tan", "sinh", "cosh", "tanh", "asin", "acos", "atan"]:
        for _ in range(100):
            x = random.choice([random.uniform(-10, 10), random.uniform(-1, 1), random.uniform(-700, 700)])
            row("x", f + "(x)", [hexof(x)])
    for f in ["pow", "atan2", "hypot", "%"]:
        for _ in range(100):
            x = random.uniform(-100, 100)
            y = random.uniform(-10, 10)
            row("x y", call(f, "x", "y"), [hexof(abs(x) if f == "pow" else x), hexof(y)])
    for _ in range(500):
        x = struct.unpack('>d', random.getrandbits(64).to_bytes(8, 'big'))[0]
        row("x", "log2(x)", [hexof(abs(x)) if x == x else hexof(1.5)])
    for exponent in range(-1074, 1024):
        row("x", "log2(x)", [struct.pack('>q', exponent + 1023 << 52).hex() if exponent > -1023
                             else struct.pack('>q', 1 << (exponent + 1074)).hex()])


def join(inputs, answers):
    for source, answer in zip(open(inputs, encoding="latin-1").read().rstrip("\n").split("\n"),
                              open(answers, encoding="latin-1").read().rstrip("\n").split("\n")):
        fields = answer.split("\t")
        sys.stdout.write(source + "\t" + fields[0] + "\t" + fields[2] + "\t" + fields[3] + "\n")


if sys.argv[1] == "join":
    join(sys.argv[2], sys.argv[3])
else:
    statement_cases()
    literal_cases()
    unary_cases()
    binary_cases()
    library_cases()
    sys.stdout.buffer.write(("\n".join(rows) + "\n").encode("latin-1"))
