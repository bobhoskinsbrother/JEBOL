#!/usr/bin/env python3
# Runs each case in cases.txt through pty-run.sh and prints the transcript the JEBOL test compares with.
#   drive.py <cases.txt> <command> [arguments...]
# The case's script is written to case.r3 in a fresh folder, which is the working directory and the
# command's last argument. Each step waits for output or types bytes; nothing sleeps.
import os
import select
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
WAIT_SECONDS = 20
NAMED_ESCAPES = {'r': b'\r', 'n': b'\n', 't': b'\t', 'e': b'\x1b', '\\': b'\\'}


def unescape(written):
    reply = bytearray()
    at = 0
    while at < len(written):
        if written[at] == '\\' and written[at + 1] == 'x':
            reply.append(int(written[at + 2:at + 4], 16))
            at += 4
        elif written[at] == '\\':
            reply += NAMED_ESCAPES[written[at + 1]]
            at += 2
        else:
            reply += written[at].encode('utf-8')
            at += 1
    return bytes(reply)


def escape(raw):
    reply = []
    for byte in raw:
        if byte == 0x5c:
            reply.append('\\\\')
        elif byte == 0x0d:
            reply.append('\\r')
        elif byte == 0x0a:
            reply.append('\\n')
        elif byte == 0x1b:
            reply.append('\\e')
        elif 0x20 <= byte < 0x7f:
            reply.append(chr(byte))
        else:
            reply.append('\\x%02x' % byte)
    return ''.join(reply)


def read_cases(path):
    with open(path, encoding='utf-8') as lines:
        for line in lines:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            name, mode, switches, script, *steps = line.split('\t')
            yield name, mode, switches.split(), unescape(script).decode('utf-8'), steps


def read_more(process, seen, deadline):
    ready, _, _ = select.select([process.stdout], [], [], max(0, deadline - time.monotonic()))
    if not ready:
        return False
    chunk = os.read(process.stdout.fileno(), 65536)
    if not chunk:
        return False
    seen += chunk
    return True


def run_case(command, name, mode, switches, script, steps):
    folder = tempfile.mkdtemp()
    try:
        named = []
        if script:
            with open(os.path.join(folder, 'case.r3'), 'w', encoding='utf-8') as file:
                file.write(script + '\n')
            named = ['case.r3']
        process = subprocess.Popen(
            [os.path.join(HERE, 'pty-run.sh'), mode, folder] + command + switches + named,
            cwd=folder, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        seen = bytearray()
        matched = 0
        deadline = time.monotonic() + WAIT_SECONDS
        verdict = ''
        for step in steps:
            if step.startswith('>'):
                wanted = unescape(step[1:])
                while seen.find(wanted, matched) < 0:
                    if not read_more(process, seen, deadline):
                        verdict = 'never saw ' + step[1:]
                        break
                if verdict:
                    break
                matched = seen.find(wanted, matched) + len(wanted)
            elif step.startswith('<'):
                process.stdin.write(unescape(step[1:]))
                process.stdin.flush()
            elif step == '.':
                process.stdin.close()
        while read_more(process, seen, deadline):
            pass
        try:
            process.wait(timeout=max(0, deadline - time.monotonic()))
        except subprocess.TimeoutExpired:
            verdict = verdict or 'timed out'
            process.kill()
            process.wait()
        return transcript(name, folder, seen, verdict)
    finally:
        shutil.rmtree(folder)


def read_text(folder, name):
    path = os.path.join(folder, name)
    if not os.path.exists(path):
        return None
    with open(path, 'rb') as file:
        return file.read()


def transcript(name, folder, seen, verdict):
    before, after = read_text(folder, 'before'), read_text(folder, 'after')
    exit_code = read_text(folder, 'exit')
    terminal = 'pipe' if before is None else ('restored' if before == after else 'changed')
    lines = ['== ' + name,
             'stdout: ' + escape(seen),
             'stderr: ' + escape(read_text(folder, 'stderr') or b''),
             'exit: ' + (exit_code.decode().strip() if exit_code else 'none'),
             'terminal: ' + terminal]
    if verdict:
        lines.append('verdict: ' + verdict)
    return '\n'.join(lines)


def main():
    cases, command = sys.argv[1], sys.argv[2:]
    for case in read_cases(cases):
        print(run_case(command, *case), flush=True)


if __name__ == '__main__':
    main()
