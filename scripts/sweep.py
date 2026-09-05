"""Turn a suite file into a probe script and diff r3-head against JEBOL.

    python3 scripts/sweep.py image-test.r3

Rewrites every `--assert X` in the file as `probe try [X]`, runs the result
through a real Rebol and through JEBOL, and prints the assertions whose two
answers differ. It is the fastest way to see what a suite file actually gets
wrong, because it shows both answers side by side rather than a pass or a
fail.

Needs `./r3-head` (a built 3.22.5) and a compiled JEBOL:
`./gradlew compileTestJava` first, since the JEBOL side runs through
`org.jebol.suite.SweepRunner`.

Two things it will not tell you:

  * It stops being useful once the probe script itself dies -- everything
    after the first raise reads as a difference. When a file shows "N
    assertions, N differ", fix the first one and sweep again.
  * A group name in known-gaps.txt is not always the group the assertion is
    in. The slicer takes the last `===start-group===` it saw at the top
    level, and files like codecs-test.r3 nest theirs inside
    `if find codecs 'png [...]`, so a hundred entries can all claim to be in
    the first group.
"""
import collections
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRATCH = os.path.join(REPO, "build", "sweep")
os.makedirs(SCRATCH, exist_ok=True)
JAVA = os.path.expanduser(
    "~/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4+7/Contents/Home/bin/java")


def balance(text, state):
    depth, brace, instr = state
    i = 0
    while i < len(text):
        c = text[i]
        if instr:
            if c == "^":
                i += 2
                continue
            if c == '"':
                instr = False
        elif brace > 0:
            if c == "^":
                i += 2
                continue
            if c == "{":
                brace += 1
            elif c == "}":
                brace -= 1
        else:
            if c == ";":
                break
            if c == '"':
                instr = True
            elif c == "{":
                brace = 1
            elif c in "[(":
                depth += 1
            elif c in "])":
                depth -= 1
        i += 1
    return (depth, brace, instr)


def probe_script(path):
    raw = open(path).read().split("\n")
    src = []
    for line in raw:
        stripped = line.strip()
        if stripped.startswith("--test--") and "--assert" in stripped:
            cut = stripped.index("--assert")
            src.append(stripped[:cut])
            src.append(stripped[cut:])
        else:
            src.append(line)

    out = ["Rebol []"]
    started = False
    i = 0
    n = 0
    sources = {}
    while i < len(src):
        line = src[i]
        stripped = line.strip()
        if stripped.startswith("~~~start-file~~~"):
            started = True
            i += 1
            continue
        if not started:
            i += 1
            continue
        if stripped.startswith("~~~end-file~~~"):
            break
        if stripped.startswith("===start-group===") or stripped.startswith("===end-group==="):
            i += 1
            continue
        if stripped.startswith("--test--"):
            i += 1
            continue
        if stripped.startswith("--assert"):
            expr = stripped[len("--assert"):]
            state = balance(expr, (0, 0, False))
            while state != (0, 0, False):
                i += 1
                expr += "\n" + src[i]
                state = balance(src[i], state)
            n += 1
            sources[n] = expr.strip()
            out.append('prin ["A%d " ]' % n)
            out.append("probe try [" + expr + "\n]")
            i += 1
            continue
        out.append(line)
        i += 1
    return "\n".join(out) + "\n", sources


def answers(path):
    seq = collections.OrderedDict()
    cur = None
    for line in open(path, errors="replace").read().split("\n"):
        m = re.match(r'^A(\d+) (.*)$', line)
        if m:
            cur = int(m.group(1))
            seq.setdefault(cur, []).append(m.group(2))
        elif cur is not None and seq[cur]:
            seq[cur][-1] += "\n" + line
    return seq


def main():
    name = sys.argv[1]
    if not name.endswith(".r3"):
        name += "-test.r3"
    script, sources = probe_script(REPO + "/src/test/resources/rebol-suite/" + name)
    probe = SCRATCH + "/probe-" + name
    open(probe, "w").write(script)

    r3out = SCRATCH + "/probe.r3out"
    jbout = SCRATCH + "/probe.jbout"
    with open(r3out, "w") as writing:
        subprocess.run([REPO + "/r3-head", probe], stdout=writing,
                       stderr=subprocess.STDOUT, cwd=REPO)
    with open(jbout, "w") as writing:
        script_file = os.path.join(SCRATCH, "sweep-script.r3")
        with open(script_file, "w") as handle:
            handle.write(script)
        subprocess.run([JAVA, "-cp",
                        "build/classes/java/main:build/classes/java/test:"
                        "build/resources/main:build/resources/test",
                        "org.jebol.suite.SweepRunner", script_file],
                       stdout=writing, stderr=subprocess.STDOUT, cwd=REPO)

    left = answers(r3out)
    right = answers(jbout)
    differing = [k for k in left if left.get(k) != right.get(k)]
    print("%d assertions, %d differ" % (len(sources), len(differing)))
    print(differing)
    for k in differing[:int(sys.argv[2]) if len(sys.argv) > 2 else 12]:
        print("\n--- A%d: %s" % (k, sources.get(k, "?")[:300]))
        print("  r3 : %s" % str(left.get(k))[:300])
        print("  jb : %s" % str(right.get(k))[:300])


main()
