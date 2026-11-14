"""Run Rebol's own pre-make with ./r3-head and with the JEBOL jar, and compare
every file each writes.

    python3 scripts/compare-pre-make.py [core|bulk]

Each interpreter gets a fresh copy of rebol3-source/ at the same absolute path,
so the paths pre-make writes into its files are the same. It resolves the nest
with scripts/resolve-nest.r3, which must give the same spec from both, then runs
make/pre-make.r3 from make/, as build-r3.sh does.

Pre-make stamps the build with `now` in three places: the copyright line at the
head of the boot data and of the host init code, and the version string in
gen-bootdefs.h. The two zlib blobs, Native_Specs in b-boot.c and Reb_Init_Code
in host-init.h, are decompressed and compared without their first line; their
sizes in gen-boot.h and host-init.h follow the compressed length and are set
aside with them. Every other byte of every file must be equal.

Needs ./r3-head and build/libs/jebol-3.22.5.jar.
"""

import difflib
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
R3 = [str(ROOT / "r3-head")]
JEBOL = ["java", "-jar", str(ROOT / "build/libs/jebol-3.22.5.jar")]

STAMPED_LINE = re.compile(
    rb"Copyright (?:\(C\) )?REBOL Technologies .*|Rebol/\w+ [\d.]+ \(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d UTC\)")
SIZE_OF_A_BLOB = re.compile(rb"#define (NAT_SPEC_SIZE|REB_INIT_SIZE) \d+")
BLOB = re.compile(rb"(Native_Specs|Reb_Init_Code)\[[^\]]*\] = \{(.*?)\}", re.S)
BYTE = re.compile(rb"0x([0-9A-Fa-f]{2})")


def rebol_arch():
    return "arm64" if platform.machine() in ("arm64", "aarch64") else "x86_64"


def rebol_os():
    return "macos" if sys.platform == "darwin" else "linux"


def run_pre_make(interpreter, work, product, name):
    tree = work / "rebol3-source"
    if tree.exists():
        shutil.rmtree(tree)
    shutil.copytree(ROOT / "rebol3-source", tree, symlinks=True)
    spec = work / f"spec-{name}.reb"
    subprocess.run(interpreter + [str(ROOT / "scripts/resolve-nest.r3"), str(tree / "make") + "/",
                                  product, str(spec), rebol_arch(), rebol_os()],
                   check=True, stdout=subprocess.DEVNULL)
    started = time.monotonic()
    printed = subprocess.run(interpreter + ["pre-make.r3", str(spec)], cwd=tree / "make",
                             capture_output=True)
    took = time.monotonic() - started
    if printed.returncode != 0 or b"DONE" not in printed.stdout:
        sys.stdout.buffer.write(printed.stdout[-3000:] + printed.stderr[-3000:])
        raise SystemExit(f"pre-make did not finish under {name}")
    kept = work / name
    tree.rename(kept)
    print(f"{name}: pre-make took {took:.2f}s")
    return kept, spec.read_bytes()


def without_stamps(content):
    def opened(blob):
        name, body = blob.group(1), blob.group(2)
        packed = bytes(int(digits, 16) for digits in BYTE.findall(body))
        if name == b"Native_Specs":
            packed = packed[4:]
        text = zlib.decompress(packed)
        return name + b" = {decompressed\n" + text + b"\n}"
    content = BLOB.sub(opened, content)
    content = SIZE_OF_A_BLOB.sub(rb"#define \1 (follows the compressed length)", content)
    return STAMPED_LINE.sub(b"(stamped)", content)


def compare(by_r3, by_jebol):
    differing = []
    names = sorted({p.relative_to(by_r3) for p in by_r3.rglob("*") if p.is_file()}
                   | {p.relative_to(by_jebol) for p in by_jebol.rglob("*") if p.is_file()})
    for name in names:
        left, right = by_r3 / name, by_jebol / name
        if not left.exists() or not right.exists():
            differing.append(name)
            print(f"only under {'jebol' if right.exists() else 'r3'}: {name}")
            continue
        a, b = left.read_bytes(), right.read_bytes()
        if a == b or without_stamps(a) == without_stamps(b):
            continue
        differing.append(name)
        print(f"differs: {name}")
        shown = difflib.unified_diff(without_stamps(a).decode("utf-8", "replace").splitlines(),
                                     without_stamps(b).decode("utf-8", "replace").splitlines(),
                                     "r3", "jebol", lineterm="", n=1)
        for line in list(shown)[:20]:
            print("    " + line)
    print(f"{len(names)} files, {len(differing)} differ")
    return not differing


def main():
    product = sys.argv[1] if len(sys.argv) > 1 else "bulk"
    with tempfile.TemporaryDirectory() as scratch:
        work = Path(scratch)
        by_r3, spec_r3 = run_pre_make(R3, work, product, "r3")
        by_jebol, spec_jebol = run_pre_make(JEBOL, work, product, "jebol")
        if spec_r3 != spec_jebol:
            raise SystemExit("resolve-nest.r3 gave different specs")
        sys.exit(0 if compare(by_r3, by_jebol) else 1)


if __name__ == "__main__":
    main()
