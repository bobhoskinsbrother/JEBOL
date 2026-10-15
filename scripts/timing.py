"""Measure where a JEBOL boot goes, and how fast the interpreter runs.

    python3 scripts/timing.py boot [runs]
    python3 scripts/timing.py speed [workload...]

`boot` builds interpreters on the JVM and prints the first call, then the
median, p95, min and max of `runs` (default 20) warm `Interpreter.create()`.

`speed` runs every workload in `src/testFixtures/resources/timing` through a real
Rebol 3.22.5 (`./r3-head`) and through JEBOL on the JVM, and prints one table:
the milliseconds each took as each interpreter measures itself (`stats/timer`
around the timed expression), the values each walked (`stats/evals`), and the
ratio.

Both verbs need the test fixtures compiled first: `./gradlew testFixturesClasses`.

What the workload files isolate, one each:

  * `count-global`   a set-word on a global, a native call and arithmetic
  * `count-local`    the same loop with the counter in a function's own slot
  * `calls`          one function call per iteration
  * `fib`            self-recursive calls and control flow
  * `series`         growing a block with `append`
  * `strings`        growing a string a character at a time
  * `lookup`         a path into a context object

Reading the numbers:

  * The JVM side is a fresh process per workload, so JVM startup is inside
    `time` but outside the figure the table uses, which is the workload's own
    `stats/timer` around the timed expression only.
  * A fresh JVM runs the first, un-JITted part of a workload necessarily
    slower. With the sizes here that prefix is a small share; treat small
    ratios between nearby workloads as noise and large ones as findings.
  * `stats/evals` counts values walked. Each interpreter counts its own way,
    and both count on every step.
  * Sizes are chosen for JEBOL to take about a second each. Only the ratios
    mean anything across machines.
"""
import glob
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLASSES = ":".join(os.path.join(REPO, part) for part in [
    "build/classes/java/main",
    "build/classes/java/testFixtures",
    "build/resources/main",
    "build/resources/testFixtures",
])
WORKLOADS = os.path.join(REPO, "src", "testFixtures", "resources", "timing")
ANSWER = re.compile(r"workload (\S+) evals (\d+) ms (\d+)")


def java():
    from_environment = os.environ.get("JAVA_HOME")
    if from_environment and os.path.exists(os.path.join(from_environment, "bin", "java")):
        return os.path.join(from_environment, "bin", "java")
    downloaded = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/jdks/eclipse_adoptium-25*/jdk-*/Contents/Home/bin/java")))
    if downloaded:
        return downloaded[-1]
    return shutil.which("java") or "java"


def boot(asked_for):
    command = [java(), "-cp", CLASSES, "org.jebol.timing.BootTime"]
    if asked_for:
        command.append(asked_for[0])
    finished = subprocess.run(command, cwd=REPO, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    sys.stdout.write(finished.stdout)
    return finished.returncode


def workload_files(asked_for):
    if asked_for:
        return [os.path.join(WORKLOADS, name if name.endswith(".r3") else name + ".r3")
                for name in asked_for]
    return sorted(glob.glob(os.path.join(WORKLOADS, "*.r3")))


def ran(file, interpreter):
    if interpreter == "r3":
        command = [os.path.join(REPO, "r3-head"), file]
    else:
        command = [java(), "-cp", CLASSES, "org.jebol.timing.SpeedRunner", file]
    try:
        finished = subprocess.run(command, cwd=REPO, text=True, timeout=600,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    except subprocess.TimeoutExpired:
        return None, "gave up after ten minutes"
    found = ANSWER.search(finished.stdout)
    if not found:
        return None, finished.stdout.strip()[-300:]
    return (int(found.group(2)), int(found.group(3))), None


def speed(asked_for):
    rows = []
    for file in workload_files(asked_for):
        name = os.path.basename(file)[:-3]
        one = {}
        for interpreter in ("r3", "jebol"):
            measured, problem = ran(file, interpreter)
            if problem is not None:
                print("=== %s: %s said: %s" % (name, interpreter, problem))
                return 1
            one[interpreter] = measured
        rows.append((name, one["jebol"], one["r3"]))
    print("%-14s %10s %12s %10s %12s %8s" %
          ("workload", "jebol ms", "jebol evals", "r3 ms", "r3 evals", "ratio"))
    for name, jebol, r3 in rows:
        ratio = jebol[1] / r3[1] if r3[1] else float("inf")
        print("%-14s %10d %12d %10d %12d %7.1fx" %
              (name, jebol[1], jebol[0], r3[1], r3[0], ratio))
    return 0


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("boot", "speed"):
        print(__doc__)
        return 2
    return boot(sys.argv[2:]) if sys.argv[1] == "boot" else speed(sys.argv[2:])


if __name__ == "__main__":
    sys.exit(main())
