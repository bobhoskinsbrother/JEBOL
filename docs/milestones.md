# Milestones

Only milestone 1 was agreed at the outset. Everything after it is a
proposal, and the ordering past milestone 2 is negotiable.

Done means a falsifiable test passes, not a feeling of completion. Where a
milestone has no exit criterion yet, that is a gap to fill before starting
it, not an oversight to live with.

## 1. The language runs -- done

Reader, value model, evaluator walk, path evaluation, infix operators,
around forty natives, and a REPL. Evaluation state on the heap in a stack
the interpreter owns. One instance, one thread.

**Done when** every entry in `corpus/` passes except the one marked
`r2-only`, and all fourteen programs in `corpus/sources/` load and survive
a round trip through MOLD.

Large. Most of the risk in the project lives here.

## 2. Objects and contexts -- done, apart from modules

`make object!`, `context`, `in`, `bind`, the binding rules. Modules are
outstanding and nothing yet needs them.

Moved ahead of everything else structural because it is a dependency of
almost all of it. VID faces are objects; `face/color` and `face/offset` in
the demo sources are field access on an object. Rendering cannot start
before this exists.

Medium sized and deep. Binding is where REBOL stops being obvious.

## 3. The standard library -- done

The rest of the natives: series, string, maths, conversion, formatting.

**Done when** a second corpus, harvested from the Core guide chapters not
yet used, passes.

Large, shallow, and the easiest to parallelise. Realistically this
continues alongside later milestones rather than finishing cleanly.

## 4. Embedding, and production operability -- done, apart from profiling

Split out from interop, because they are different things and only this
half is what the project is for. A Java application creates an
interpreter, hands it a script, and gets a value back, under bounds it
sets. Nothing here requires REBOL to call Java.

- a `Context` lifecycle a web application can hold per request
- a wall-clock bound, so a slow script cannot hold a request open
- a memory bound per instance
- interruption that actually works, which needs the rest of the evaluator
  moved off the host stack
- frames a profiler can read, so a slow script can be found in production
  rather than reasoned about

**Done when** a script that loops forever is stopped by its deadline, the
request completes, and a profile of the run names the REBOL that was
slow rather than naming `Evaluator.walk` four thousand times.

The first two hold: `EmbeddingTest` stops a `while [true]` loop at its
deadline, cancels one from another thread, and runs forty interpreters
across eight threads without them seeing each other. The profiling clause
does not, and is the one thing outstanding here.

That last one is easy to leave out and expensive to add later. It is the
difference between running in production and merely being deployed there.

## 5. Two-way Java interop -- done

REBOL code calling Java, host values crossing into REBOL, and the
conversion rules. `integer!` to `long` and `block!` to `List` are settled
in `docs/decisions.md`; the rest is not.

Worth taking the shape of GraalVM's `HostAccess` here: an explicit policy
the embedder sets for what the guest may reach and whether it may mutate
it, rather than a paragraph of documentation saying it is their problem.
The decision that mutability is the caller's concern stands; making it a
choice expressed in code is better than making it advice.

**Done when** Java can create a context, evaluate REBOL that calls a Java
method, and read a REBOL value back out.

## 6. Rendering: dialects to markup -- done for VID

The VID and `draw` dialects rendered to HTML, CSS and SVG. Static output
only: a layout goes in, markup comes out.

This is the milestone that makes JEBOL useful in a web context, and it is
why graphics are on this list rather than dismissed. The target is markup,
not a window. A dialect is a block interpreted by a function, so this
needs the evaluator and objects and nothing exotic beyond them.

**Done when** the seven demo programs that carry no event handlers render
to markup: `color-names.r`, `diagram.r`, `tile-game.r`, `emailer.r`,
`feedback.r`, `font-lab.r`, `effect-lab.r`.

Large. See the open fork below on how faithful the rendering should be.

## 7. Interactive rendering -- done

Built after 9 rather than before it, because an event arriving from a
browser needs something serving the page first. Noticed while building 6
rather than while planning, and left in place rather than renumbered so the
dependency stays visible.

The shape taken is the one Phoenix LiveView and Hotwire use: the view lives
on the server, an event names which face was touched, the block runs, and
the page is rendered again rather than patched. Nothing but markup crosses
to the browser, so a script never runs anywhere but here.

Building it found a flaw in the first attempt: re-running the source on
every render undid whatever an action had changed, because the source sets
up the state as well as describing the page. The layout is now built once
and the words in it looked up afresh on each render.

The event model. A browser event arrives, the REBOL handler in a `feel`
or `engage` block runs, and the view is re-rendered or patched. The same
shape as Phoenix LiveView or Hotwire.

**Done when** the seven demos that do carry event handlers work in a
browser: `clock1.r`, `clock.r`, `calculator.r`, `rebodex.r`, `gel.r`,
`mines.r`, `rebtris.r`.

Large, and the hardest to specify, because REBOL's event model was built
for a local window with no round trip in it.

## 8. PARSE -- done

The dialect. Effectively its own project, with its own corpus.

## 9. Ports and I/O -- done for files

Files, through a port the host supplies. A script given no port reaches
nothing, and a port is rooted at a directory it cannot climb out of,
whether by `..` or by naming an absolute path. Network and the wider scheme
model are not built.

Five of the fourteen demos need I/O at runtime: `effect-lab.r`, `feedback.r`, `gel.r`, `rebodex.r`, `rebtris.r`.

---

## The open fork on rendering

How faithful should VID be?

**Faithful.** Reimplement VID's layout algorithm, styles and positioning
so a layout produces the same pixels it would have in View. The demo
sources use 24 distinct style and layout words between them, and
`across`, `below`, `origin` and `space` are a real positioning model that
would need reimplementing on top of a box model that works differently.
Expensive, and the result is HTML that fights the browser.

**VID-shaped.** Accept the dialect and render idiomatic HTML, letting the
browser lay out. Existing layouts mostly work; pixel-exact ones do not.
Far cheaper and the output is something a web developer can style.

**Taken: VID-shaped**, on the recommendation above and without the decision
being made, because the milestone could not start otherwise. Stated here so
it is a visible assumption rather than a silent one. Pixel fidelity to a
2001 desktop toolkit is not what makes the language useful in a browser,
and chasing it produces markup nobody wants to work with. Reversing it
means rewriting `domain/render`, not the language.
