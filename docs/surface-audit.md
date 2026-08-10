# What JEBOL is missing, measured rather than discovered

This is the whole gap between JEBOL's library and a real R3 3.22.1, taken
in one pass instead of one failing assertion at a time.

## How it was taken

The binary answers `spec-of` for every word in `system/contexts/lib`, and
a spec carries the argument names, the datatypes each argument accepts,
and the refinements. That is a complete machine-readable contract for all
580 of its functions, and it needs no C source:

```rebol
foreach w sort words-of system/contexts/lib [
    set/any 'v try [get/any in system/contexts/lib w]
    if all [not error? :v  any-function? :v] [
        ; name, then each argument with its accepted types, then each refinement
    ]
]
```

JEBOL's side is `SurfaceReportTest`, which reads the native registry
directly. It has to: JEBOL's natives carry no REBOL-readable spec, so
`spec-of :append` answers none where the binary answers its full contract.
That is itself a gap, and it is why the two halves of this audit are
gathered differently.

The name list comes from a booted interpreter rather than from the
registry, because the prelude and the borrowed Rebol files define a third
of the library and none of it is in the registry. Reading the registry
alone said 294 functions were missing; 120 of those were sitting in
`prelude.reb`.

## Gap 1: 174 functions R3 has and JEBOL has not

Most of these are the host's rather than the language's, and belong with
the decision about where the host boundary sits:

> access-os ask browse call cd change-dir close create delete delete-dir
> dir dir? dir-tree dirize echo flush get-env in-dir input launch list-dir
> list-env ls make-dir mkdir modified? more now open open? pwd query
> read-key rebol-console recycle rename request-color request-dir
> request-file request-password rm secure set-env set-scheme set-user
> size? stats su suffix? undirize wait wait-key what-dir

Cryptography and codecs, which are libraries rather than language:

> checksum compress debase decloak decompress dehex ecdh ecdsa enbase
> encloak enhex file-checksum iconv rc4 rsa rsa-init swap-endian

Images and colour, which need a graphics model JEBOL has not got:

> as-blue as-cyan as-gray as-green as-purple as-red as-white as-yellow
> ansi-colorize blur color-distance grayscale hsv-to-rgb image image-diff
> luminosity map-event map-gob-offset premultiply resize rgb-to-hsv tint
> unfilter

That leaves the ones that are ordinary language, and these are the work:

> all-of any-of arctangent2 cause-error check clean-path clos complement?
> confirm continue default delect do-callback do-codec do-commands dump
> dump-obj enum evoke exp filter find-script form-oid forskip fraction
> funco funct generate has help import intern load-extension map module
> register script? source split-lines split-path stack to-local-file
> to-real-file to-rebol-file to-relative-file trace update usage version
> what wildcard wildcard? wrap

## Gap 2: 28 functions that exist but refuse a refinement

Every one of these was checked by calling it. Seventeen of the eighteen
sampled raise `no-refine`, so the list is real rather than an artefact of
how the audit reads JEBOL's registry.

| Function | Refinements it has not got |
| --- | --- |
| `copy` | `/deep` `/types` |
| `uppercase`, `lowercase` | `/part` |
| `reverse` | `/part` |
| `sort` | `/unstable` |
| `type?` | `/word` |
| `find` | `/with` |
| `select` | `/reverse` `/with` |
| `remove-each` | `/count` |
| `put` | `/skip` |
| `protect` | `/lock` |
| `context` | `/only` |
| `if`, `either`, `unless` | `/only` |
| `union`, `intersect`, `difference`, `exclude` | `/case` `/skip` |
| `%%`, `modulo` | `/floor` |
| `index?`, `indexz?` | `/xy` |
| `invalid-utf?` | `/utf` |
| `split` | `/at` `/parts` |
| `load` | `/as` `/header` |
| `read`, `write` | the host's, listed for completeness |

`copy/deep` not working is the one that should have been found years
before any of the others.

## Gap 3: functions named with a slash instead of taking a refinement

`copy/part` appears in JEBOL's surface as a *function called `copy/part`*
rather than as `copy` with a refinement. So does `load/all`, until this
session. A word with a slash in it is not a refinement: it answers the one
call it is named for and leaves every other combination raising
`no-refine`, and it never shows up as a missing refinement because the
name exists.

Searching JEBOL's registry for a defined name containing a slash finds
them all, and each one is the same fix: fold it back into its parent as a
declared refinement.

## Gap 4: argument datatypes -- none, and that is the finding

The third comparison expands R3's typeset names (`series!`, `any-string!`,
`number!` and the rest) into the datatypes they stand for and checks every
shared native's every argument. **Not one parameter accepts fewer
datatypes than the binary's.**

That matters because it rules out the obvious reading of the hundred-odd
assertions failing on `expect-arg`. The declared parameters are not too
narrow; the refusals happen inside the native bodies, after the argument
has been let through the door. `to integer! none` was one of these: the
parameter accepted it and a shared numeric helper raised `expect-arg`
several levels down, where the error also named the wrong thing.

So `expect-arg` is not one gap to close but a list of individual bodies to
put to the binary, and this audit does not shorten that job.

## Gap 5: a borrowed file's local names leak into the library

Porting EXP turned this up. Rebol's own `codec-json.reb` writes

```rebol
exp:  [[#"e" | #"E"] opt [#"+" | #"-"] some digit]
```

as one of its parse rules. In a real R3 that file is loaded inside its own
context and the name goes nowhere; JEBOL loads the borrowed files flat, so
it lands in the library and replaces the EXP that was just defined. The
native is still there and unreachable.

Every borrowed file is a candidate for this, and the damage is silent: the
name still answers, it just answers the wrong thing. Two ways out, and
neither is small -- load each borrowed file into its own context the way
R3 does, or check after loading that no borrowed file has replaced a name
the library already had. The second is a test rather than a fix and would
at least make the list visible.

Recorded here rather than worked around, because renaming EXP would hide
it and the next collision would be found the same slow way.

## Progress against this audit

Ported since it was taken, 23 in all: `all-of`, `any-of`, `arctangent2`,
`cause-error`, `clos`, `complement?`, `continue`, `default`, `dir?`,
`dirize`, `exp` (shadowed, see above), `find-script`, `forskip`,
`fraction`, `funco`, `funct`, `has`, `map`, `script?`, `split-lines`,
`suffix?`, `undirize`, `wildcard?` and `wrap`, plus the whole 45-strong
TO-X family.

Each one found a defect that the suite had not. `to paren!` and `to hash!`
did not exist, and ALL-OF needs the first to build its loop body.
`make object! 0` threw a Java exception. `make error!` did not evaluate
its spec, so every error CAUSE-ERROR raised carried the wrong name. An
error value dropped its argument. TAIL? refused a map, which EMPTY? needs.

## What blocks the rest

Three things, and none of them is more porting.

`intern` and `module` need `system/contexts/user`. JEBOL has a user
context -- `Interpreter.userContext`, a child of the library, and the
place where source read at run time now puts its words -- but nothing
publishes it as `system/contexts/user`, so no REBOL code can reach it.
The remaining work is exposing it, not building it.

`clean-path`, `split-path`, `wildcard`, `dir-tree` and `file-type?` all
call something that reads the file system. They are language functions
sitting on host natives, so the host boundary decides them too.

`dump-obj`, `help`, `about`, `usage` and the five `log-*` functions need
`emit`, `reform`, `ansi` and `system/options`. That is a console layer
rather than a language one.
