package org.jebol.adapter.web;

final class WebScreenPage {

    private WebScreenPage() {
    }

    static final String HTML = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>JEBOL</title>
            <style>
              html, body { margin: 0; height: 100%; background: #101216; }
              canvas { display: block; }
            </style>
            </head>
            <body>
            <canvas id="screen"></canvas>
            <script>
            const canvas = document.getElementById('screen');
            const brush = canvas.getContext('2d');

            function tell(fields) {
              fetch('/event', { method: 'POST', body: JSON.stringify(fields) });
            }

            function sayHowBigWeAre() {
              tell({ kind: 'measure', wide: window.innerWidth, high: window.innerHeight });
            }

            function paint(picture) {
              canvas.width = picture.wide;
              canvas.height = picture.high;
              brush.clearRect(0, 0, picture.wide, picture.high);
              for (const step of picture.paint) {
                brush.save();
                brush.beginPath();
                brush.rect(step.clip.across, step.clip.down, step.clip.wide, step.clip.high);
                brush.clip();
                if (step['clip-shape'] && step['clip-shape'].length) {
                  const t = step.transform;
                  brush.save();
                  if (t) brush.transform(t[0], t[1], t[2], t[3], t[4], t[5]);
                  const inside = asPath(step['clip-shape']);
                  brush.restore();
                  if (t) {
                    const placed = new Path2D();
                    const shift = new DOMMatrix([t[0], t[1], t[2], t[3], t[4], t[5]]);
                    placed.addPath(inside, shift);
                    brush.clip(placed);
                  } else {
                    brush.clip(inside);
                  }
                }
                brush.globalAlpha = step.opacity / 255;
                if (step.kind === 'fill') {
                  brush.fillStyle = step.colour;
                  brush.fillRect(step.across, step.down, step.wide, step.high);
                } else if (step.kind === 'writing') {
                  writeTheLine(step);
                } else if (step.kind === 'picture') {
                  showPicture(step);
                } else if (step.kind === 'drawing') {
                  drawShape(step);
                }
                brush.restore();
              }
            }

            function fontOf(run) {
              return (run.italic ? 'italic ' : '') + (run.bold ? 'bold ' : '')
                  + run.size + 'px sans-serif';
            }

            function measured(run, text) {
              brush.font = fontOf(run);
              const found = brush.measureText(text);
              return { wide: found.width, ascent: found.fontBoundingBoxAscent,
                       descent: found.fontBoundingBoxDescent };
            }

            function aPiece(runs, run, from, text) {
              return { run: run, from: from, text: text, font: runs[run],
                       wide: measured(runs[run], text).wide };
            }

            function theLinesBetweenNewlines(runs) {
              const lines = [];
              let pieces = [];
              runs.forEach((run, index) => {
                const characters = [...run.text];
                let from = 0;
                for (let at = 0; at <= characters.length; at++) {
                  const endsTheRun = at === characters.length;
                  if (endsTheRun || characters[at] === '\\n') {
                    pieces.push(aPiece(runs, index, from, characters.slice(from, at).join('')));
                    if (!endsTheRun) {
                      lines.push(pieces);
                      pieces = [];
                      from = at + 1;
                    }
                  }
                }
              });
              if (pieces.length) lines.push(pieces);
              return lines;
            }

            function theLinesOf(runs, room) {
              return theLinesBetweenNewlines(runs).flatMap(pieces => brokenToFit(runs, pieces, room));
            }

            function isASpace(letter) {
              return letter.character === ' ' || letter.character === '\\t';
            }

            function theLettersOf(pieces) {
              const letters = [];
              pieces.forEach(piece => {
                if (piece.text === '') letters.push({ run: piece.run, at: piece.from, character: null });
                [...piece.text].forEach((character, index) =>
                  letters.push({ run: piece.run, at: piece.from + index, character: character }));
              });
              return letters;
            }

            function thePiecesOf(runs, letters) {
              const pieces = [];
              let start = 0;
              while (start < letters.length) {
                let end = start + 1;
                while (end < letters.length && letters[end].run === letters[start].run
                       && letters[start].character !== null && letters[end].character !== null) end++;
                const text = letters.slice(start, end).filter(letter => letter.character !== null)
                    .map(letter => letter.character).join('');
                pieces.push(aPiece(runs, letters[start].run, letters[start].at, text));
                start = end;
              }
              return pieces;
            }

            function widthOf(pieces) {
              return pieces.reduce((sum, piece) => sum + piece.wide, 0);
            }

            function aLineOfTheLetters(runs, letters) {
              let printed = letters.length;
              while (printed > 0 && isASpace(letters[printed - 1])) printed--;
              return aLineOf(thePiecesOf(runs, letters), widthOf(thePiecesOf(runs, letters.slice(0, printed))));
            }

            function brokenToFit(runs, pieces, room) {
              const letters = theLettersOf(pieces);
              if (room === Infinity || !letters.length) return [aLineOf(pieces, widthOf(pieces))];
              const fits = some => widthOf(thePiecesOf(runs, some)) <= room;
              const broken = [];
              let lineStart = 0, at = 0;
              while (at < letters.length) {
                let wordEnd = at;
                while (wordEnd < letters.length && !isASpace(letters[wordEnd])) wordEnd++;
                if (fits(letters.slice(lineStart, wordEnd))) {
                  at = wordEnd;
                  while (at < letters.length && isASpace(letters[at])) at++;
                } else if (at > lineStart) {
                  broken.push(aLineOfTheLetters(runs, letters.slice(lineStart, at)));
                  lineStart = at;
                } else {
                  let fitting = at + 1;
                  while (fitting < wordEnd && fits(letters.slice(at, fitting + 1))) fitting++;
                  broken.push(aLineOfTheLetters(runs, letters.slice(at, fitting)));
                  lineStart = fitting;
                  at = fitting;
                }
              }
              if (lineStart < letters.length || !broken.length) {
                broken.push(aLineOfTheLetters(runs, letters.slice(lineStart)));
              }
              return broken;
            }

            function aLineOf(pieces, wide) {
              let ascent = 0, descent = 0;
              pieces.forEach(piece => {
                const extent = measured(piece.font, piece.text);
                ascent = Math.max(ascent, extent.ascent);
                descent = Math.max(descent, extent.descent);
              });
              return { pieces: pieces, wide: wide, ascent: ascent, descent: descent,
                       high: ascent + descent };
            }

            function theRoomForEachLine(step) {
              const layout = step.layout;
              return layout.wraps ? step.wide - layout['origin-across'] - layout['margin-across'] : Infinity;
            }

            function placeTheLines(step, lines) {
              const layout = step.layout;
              const roomAcross = step.wide - layout['origin-across'] - layout['margin-across'];
              const roomDown = step.high - layout['origin-down'] - layout['margin-down'];
              const tallness = lines.reduce((sum, line) => sum + line.high, 0);
              const spareDown = roomDown - tallness;
              let top = step.down + layout['origin-down']
                  + { top: 0, middle: spareDown / 2, bottom: spareDown }[layout.valign];
              lines.forEach(line => {
                const spareAcross = roomAcross - line.wide;
                line.left = step.across + layout['origin-across']
                    + { left: 0, centre: spareAcross / 2, right: spareAcross }[layout.align];
                line.top = top;
                top += line.high;
              });
            }

            function whereTheCaretIs(lines, run, character) {
              for (const line of [...lines].reverse()) {
                let along = line.left;
                for (const piece of line.pieces) {
                  const length = [...piece.text].length;
                  if (piece.run === run && character >= piece.from && character <= piece.from + length) {
                    const before = [...piece.text].slice(0, character - piece.from).join('');
                    return { across: along + measured(piece.font, before).wide, top: line.top, high: line.high };
                  }
                  along += piece.wide;
                }
              }
              const last = lines[lines.length - 1];
              return { across: last.left + last.wide, top: last.top, high: last.high };
            }

            function markTheSelection(step, lines, selection) {
              const from = whereTheCaretIs(lines, selection['from-run'], selection['from-character']);
              const to = whereTheCaretIs(lines, selection['to-run'], selection['to-character']);
              const first = from.top < to.top || (from.top === to.top && from.across <= to.across) ? from : to;
              const last = first === from ? to : from;
              brush.fillStyle = '#aac8f5';
              if (first.top === last.top) {
                brush.fillRect(first.across, first.top, last.across - first.across, first.high);
                return;
              }
              const right = step.across + step.wide;
              brush.fillRect(first.across, first.top, right - first.across, first.high);
              brush.fillRect(step.across, first.top + first.high, step.wide, last.top - first.top - first.high);
              brush.fillRect(step.across, last.top, last.across - step.across, last.high);
            }

            function writeTheLine(step) {
              const layout = step.layout;
              const lines = theLinesOf(step.runs, theRoomForEachLine(step));
              if (!lines.length) return;
              placeTheLines(step, lines);
              if (step.caret && step.caret.selection) markTheSelection(step, lines, step.caret.selection);
              if (layout['shadow-across'] !== 0 || layout['shadow-down'] !== 0) {
                writeTheRuns(lines, layout['shadow-across'], layout['shadow-down'], '#000000');
              }
              writeTheRuns(lines, 0, 0, null);
              if (step.caret) {
                const placed = whereTheCaretIs(lines, step.caret.run, step.caret.character);
                const run = step.runs[step.caret.run];
                brush.fillStyle = run ? run.colour : '#000000';
                brush.fillRect(placed.across, placed.top, 1, placed.high);
              }
            }

            function writeTheRuns(lines, movedAcross, movedDown, everyRunIn) {
              lines.forEach(line => {
                let along = line.left + movedAcross;
                line.pieces.forEach(piece => {
                  brush.font = fontOf(piece.font);
                  brush.fillStyle = everyRunIn || piece.font.colour;
                  brush.fillText(piece.text, along, line.top + line.ascent + movedDown);
                  along += piece.wide;
                });
              });
            }

            const capNames = { 'butt': 'butt', 'square': 'square', 'rounded': 'round' };
            const joinNames = { 'miter': 'miter', 'miter-bevel': 'miter',
                                'round': 'round', 'bevel': 'bevel' };

            function showPicture(step) {
              const pixels = asImageData(step);
              const held = document.createElement('canvas');
              held.width = pixels.width;
              held.height = pixels.height;
              held.getContext('2d').putImageData(pixels, 0, 0);
              const t = step.transform;
              if (t) brush.transform(t[0], t[1], t[2], t[3], t[4], t[5]);
              brush.drawImage(held, step.across, step.down,
                  step['draw-wide'], step['draw-high']);
            }

            function asGradient(fill) {
              const run = fill.gradient === 'radial'
                  ? brush.createRadialGradient(
                      fill['across-start'], fill['down-start'], 0,
                      fill['across-start'], fill['down-start'], fill.radius)
                  : brush.createLinearGradient(
                      fill['across-start'], fill['down-start'],
                      fill['across-end'], fill['down-end']);
              for (const stop of fill.stops) {
                run.addColorStop(Math.min(1, Math.max(0, stop.at)), stop.colour);
              }
              return run;
            }

            function drawShape(step) {
              const shape = asPath(step.path);
              const t = step.transform;
              brush.transform(t[0], t[1], t[2], t[3], t[4], t[5]);
              if (step.fill) {
                brush.fillStyle = step.fill.gradient
                    ? asGradient(step.fill)
                    : step.fill.colour;
                brush.fill(shape, step.fill.rule === 'even-odd' ? 'evenodd' : 'nonzero');
              }
              if (step.stroke) {
                brush.strokeStyle = step.stroke.colour;
                brush.lineWidth = step.stroke.width;
                brush.lineCap = capNames[step.stroke.cap];
                brush.lineJoin = joinNames[step.stroke.join];
                brush.setLineDash(step.stroke.dashes || []);
                brush.stroke(shape);
                brush.setLineDash([]);
              }
            }

            function asPath(steps) {
              const shape = new Path2D();
              const turn = Math.PI / 180;
              for (const piece of steps) {
                if (piece.step === 'move-to') {
                  shape.moveTo(piece.across, piece.down);
                } else if (piece.step === 'line-to') {
                  shape.lineTo(piece.across, piece.down);
                } else if (piece.step === 'quadratic-to') {
                  shape.quadraticCurveTo(piece['control-across'], piece['control-down'],
                      piece.across, piece.down);
                } else if (piece.step === 'cubic-to') {
                  shape.bezierCurveTo(piece['control-across'], piece['control-down'],
                      piece['second-across'], piece['second-down'],
                      piece.across, piece.down);
                } else if (piece.step === 'ellipse-at') {
                  shape.moveTo(piece.across + piece['radius-across'], piece.down);
                  shape.ellipse(piece.across, piece.down,
                      piece['radius-across'], piece['radius-down'], 0, 0, Math.PI * 2);
                } else if (piece.step === 'arc-to') {
                  if (piece.closes) shape.moveTo(piece.across, piece.down);
                  shape.ellipse(piece.across, piece.down,
                      piece['radius-across'], piece['radius-down'], 0,
                      piece.begins * turn, (piece.begins + piece.turns) * turn);
                  if (piece.closes) shape.closePath();
                } else if (piece.step === 'close') {
                  shape.closePath();
                }
              }
              return shape;
            }

            function asImageData(step) {
              const raw = atob(step.pixels);
              const octets = new Uint8ClampedArray(raw.length);
              for (let at = 0; at < raw.length; at++) octets[at] = raw.charCodeAt(at);
              return new ImageData(octets,
                  step['pixel-wide'] || step.wide, step['pixel-high'] || step.high);
            }

            const pictures = new EventSource('/paint');
            pictures.addEventListener('paint', message => paint(JSON.parse(message.data)));
            pictures.addEventListener('open', sayHowBigWeAre);

            const keysThatTypeNothing = {
              PageUp: 'page-up', PageDown: 'page-down', End: 'end', Home: 'home',
              ArrowLeft: 'left', ArrowUp: 'up', ArrowRight: 'right', ArrowDown: 'down',
              Insert: 'insert',
              F1: 'f1', F2: 'f2', F3: 'f3', F4: 'f4', F5: 'f5', F6: 'f6',
              F7: 'f7', F8: 'f8', F9: 'f9', F10: 'f10', F11: 'f11', F12: 'f12',
              Shift: 'shift', Control: 'control', Alt: 'alt', Pause: 'pause', CapsLock: 'capital'
            };
            const keysThatTypeAControlCharacter = {
              Backspace: 8, Tab: 9, Enter: 13, Escape: 27, Delete: 127
            };

            function tellWhereThePointerIs(kind, pointer) {
              tell({ kind: kind, across: Math.round(pointer.offsetX), down: Math.round(pointer.offsetY) });
            }

            function theCharacterTyped(pressed) {
              if (pressed.key in keysThatTypeAControlCharacter) {
                return keysThatTypeAControlCharacter[pressed.key];
              }
              return [...pressed.key].length === 1 ? pressed.key.codePointAt(0) : null;
            }

            function tellTheKey(pressed, typing, naming) {
              if (pressed.key in keysThatTypeNothing) {
                tell({ kind: naming, named: keysThatTypeNothing[pressed.key] });
                return;
              }
              const typed = theCharacterTyped(pressed);
              if (typed !== null) {
                tell({ kind: typing, code: typed });
              }
            }

            window.addEventListener('resize', sayHowBigWeAre);
            canvas.addEventListener('mousedown', pointer => tellWhereThePointerIs('down', pointer));
            canvas.addEventListener('mouseup', pointer => tellWhereThePointerIs('up', pointer));
            canvas.addEventListener('mousemove', pointer => tellWhereThePointerIs('move', pointer));
            window.addEventListener('keydown', pressed => tellTheKey(pressed, 'key', 'control'));
            window.addEventListener('keyup', pressed => tellTheKey(pressed, 'key-up', 'control-up'));
            window.addEventListener('beforeunload', () => tell({ kind: 'close' }));

            sayHowBigWeAre();
            </script>
            </body>
            </html>
            """;
}
