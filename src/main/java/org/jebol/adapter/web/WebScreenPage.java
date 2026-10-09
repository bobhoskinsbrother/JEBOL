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

            function writeTheLine(step) {
              const layout = step.layout;
              let lineWide = 0;
              let ascent = 0;
              let descent = 0;
              const widths = step.runs.map(run => {
                brush.font = fontOf(run);
                const measured = brush.measureText(run.text);
                ascent = Math.max(ascent, measured.fontBoundingBoxAscent);
                descent = Math.max(descent, measured.fontBoundingBoxDescent);
                lineWide += measured.width;
                return measured.width;
              });
              const roomAcross = step.wide - layout['origin-across'] - layout['margin-across'];
              const roomDown = step.high - layout['origin-down'] - layout['margin-down'];
              const spareAcross = roomAcross - lineWide;
              const spareDown = roomDown - (ascent + descent);
              const across = step.across + layout['origin-across']
                  + { left: 0, centre: spareAcross / 2, right: spareAcross }[layout.align];
              const baseline = step.down + layout['origin-down']
                  + { top: 0, middle: spareDown / 2, bottom: spareDown }[layout.valign] + ascent;
              if (layout['shadow-across'] !== 0 || layout['shadow-down'] !== 0) {
                writeTheRuns(step.runs, widths, across + layout['shadow-across'],
                    baseline + layout['shadow-down'], '#000000');
              }
              writeTheRuns(step.runs, widths, across, baseline, null);
            }

            function writeTheRuns(runs, widths, across, baseline, everyRunIn) {
              let along = across;
              runs.forEach((run, index) => {
                brush.font = fontOf(run);
                brush.fillStyle = everyRunIn || run.colour;
                brush.fillText(run.text, along, baseline);
                along += widths[index];
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
