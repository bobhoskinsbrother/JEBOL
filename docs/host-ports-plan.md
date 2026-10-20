# Host ports as one value, given at construction

## Context

`Interpreter` has 14 `useX(port)` methods that only forward to `Evaluator`, because
`Evaluator` holds 15 host ports as mutable fields (files, environment, images, console,
windows, screen, pause, processes, network, certificate authority, http, clipboard, midi,
tasks, machine), each defaulting to `XPort.none()`. Every host (`Repl.buildInterpreter`,
`WorkerInterpreter`, `SuiteHost`) sets them once, straight after construction. The
setters model a change that never happens and allow an interpreter to boot and run before
its ports exist. Decided: replace them with one `HostPorts` value supplied at construction;
`Bounds` stays a separate value.

## Approach

0. **Renames first** (IDE rename, every caller follows): `TrustingNoAuthority` ->
   `ZeroTrustAuthority`, `Repl.buildAnInterpreter` -> `Repl.buildInterpreter`.
   `TheInterpreterInAWorker` -> `WorkerInterpreter` is done, including its name in
   `build.gradle.kts`'s TeaVM `mainClass`. The names below use the new spellings.

1. **Spec first.** Add a guidance/rule note to `spec/embed.allium` (Interpreter entity):
   the host supplies its ports when the interpreter is built; a port it does not supply is
   that port's refusing default. `allium check` clean. No script-visible change.

2. **`HostPorts`** - new immutable value in `src/main/java/org/jebol/domain/host/`. One
   accessor per port; `HostPorts.none()` holds every port's existing `none()` (reuse
   `FilePort.none()`, `ProcessPort.none()`, `EnvironmentPort.none()`, `MachinePort.none()`
   and the rest, plus `new ZeroTrustAuthority()` and the `ThreadPause` default where those
   are the current defaults). Wither methods `withFiles(..)`, `withProcesses(..)` etc.
   return a copy - the same shape `Bounds` already uses (`grant`, `withTimeLimit`).

3. **`Evaluator`** takes `HostPorts` in its constructor and keeps one field; its 15
   per-port fields, getters and setters collapse to `ports()`. Natives change from
   `evaluator.files()` to `evaluator.ports().files()` - mechanical, via
   `ide_refactor_rename`/IDE where possible. `useHttp`'s actor swap (it changes the http
   scheme's actor) moves to construction.

4. **`Interpreter`** - factories become `create()`, `withBounds(bounds)`,
   `createOn(HostPorts, OutputPort, Bounds)` (machine now inside `HostPorts`; default
   `JavaMachine` + `ThreadPause` in the convenience factories). The 14 forwarding setters go.
   The real work in `useScreen` (root gob, draw dialect) and `useFileSystem` (shared root,
   modules directory, launcher) runs once in the constructor after the library loads.
   `followApplicationDataDirectory()` stays public - Repl calls it when `--data` moves the
   data directory.

5. **Hosts** - `Repl.buildInterpreter`, `ChosenScreen`, `WorkerInterpreter`,
   `SuiteHost`, `ThreadTasks` build a `HostPorts` and pass it. Tests that call
   `interpreter.useX(...)` switch to `Interpreter.createOn(HostPorts.none().withX(...), ...)`;
   add a small test-fixture helper if the pattern repeats.

## Tests (written before step 2's code, confirmed red)

- Unit: `HostPorts.none()` answers each port's refusing default; each wither replaces
  exactly one port and leaves the original unchanged.
- Functional: an interpreter built with a screen gets the root gob and draw dialect; one
  built with a filesystem has its modules directory and confined launcher in place at
  construction (port the existing `useFileSystem`/`useScreen` assertions in
  `HostMachineTest`, `DesktopScreenFromTheSourceTest`, `PortsTest`).
- Existing suites carry the rest unchanged in behaviour.

## Verification

`ide_diagnostics` after each step; `DependencyRuleTest`; ports, process, screen, module and
boot tests; the command-line and browser end-to-end tests, since Repl and the worker change
most. User runs `allTests`.
