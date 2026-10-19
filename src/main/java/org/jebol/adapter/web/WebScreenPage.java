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
              @font-face { font-family: 'Jebol Carried'; src: url('NotoSans-Regular.ttf'); }
            </style>
            </head>
            <body>
            <canvas id="screen"></canvas>
            <script>
            const canvas = document.getElementById('screen');
            const brush = canvas.getContext('2d');

            let eventResponse = Promise.resolve();

            function dispatch(fields) {
              eventResponse = eventResponse
                  .then(() => fetch('/event', { method: 'POST', body: JSON.stringify(fields) }))
                  .catch(() => {});
            }

            function windowSize() {
              dispatch({ type: 'measure', wide: window.innerWidth, high: window.innerHeight });
            }

            let lastPicture = null;

            function paint(picture) {
              lastPicture = picture;
              canvas.width = picture.wide;
              canvas.height = picture.high;
              brush.clearRect(0, 0, picture.wide, picture.high);
              for (const call of picture.paint) {
                paintOneCall(call);
              }
            }

            function paintOneCall(call) {
              brush.save();
              brush.beginPath();
              brush.rect(call.clip.across, call.clip.down, call.clip.wide, call.clip.high);
              brush.clip();
              if (call['clip-shape'] && call['clip-shape'].length) {
                clipToShape(call);
              }
              brush.globalAlpha = call.opacity / 255;
              if (call.type === 'fill') {
                fillPath(call);
              } else if (call.type === 'stroke') {
                strokePath(call);
              } else if (call.type === 'picture') {
                showPicture(call);
              } else if (call.type === 'text') {
                writeText(call);
              }
              brush.restore();
            }

            function isIdentity(t) {
              return !t || (t[0] === 1 && t[1] === 0 && t[2] === 0
                  && t[3] === 1 && t[4] === 0 && t[5] === 0);
            }

            function clipToShape(call) {
              const inside = asPath(call['clip-shape']);
              if (isIdentity(call.transform)) {
                brush.clip(inside);
                return;
              }
              const t = call.transform;
              const placed = new Path2D();
              placed.addPath(inside, new DOMMatrix([t[0], t[1], t[2], t[3], t[4], t[5]]));
              brush.clip(placed);
            }

            function applyTransform(call) {
              const t = call.transform;
              if (!isIdentity(t)) brush.transform(t[0], t[1], t[2], t[3], t[4], t[5]);
            }

            const capNames = { 'butt': 'butt', 'square': 'square', 'rounded': 'round' };
            const joinNames = { 'miter': 'miter', 'miter-bevel': 'miter',
                                'round': 'round', 'bevel': 'bevel' };

            function fillPath(call) {
              applyTransform(call);
              brush.fillStyle = call.gradient ? asGradient(call.gradient) : call.colour;
              brush.fill(asPath(call.path), call.rule === 'even-odd' ? 'evenodd' : 'nonzero');
            }

            function strokePath(call) {
              applyTransform(call);
              brush.strokeStyle = call.colour;
              brush.lineWidth = call.width;
              brush.lineCap = capNames[call.cap];
              brush.lineJoin = joinNames[call.join];
              brush.setLineDash(call.dashes || []);
              brush.stroke(asPath(call.path));
              brush.setLineDash([]);
            }

            function showPicture(call) {
              applyTransform(call);
              const pixels = asImageData(call);
              const held = document.createElement('canvas');
              held.width = pixels.width;
              held.height = pixels.height;
              held.getContext('2d').putImageData(pixels, 0, 0);
              brush.drawImage(held, call.across, call.down,
                  call['draw-wide'], call['draw-high']);
            }

            function fontOf(piece) {
              return (piece.italic ? 'italic ' : '') + (piece.bold ? 'bold ' : '')
                  + piece.size + 'px "Jebol Carried", sans-serif';
            }

            function writeText(call) {
              if (call.selection && call.selection.length) {
                brush.fillStyle = call['selection-colour'];
                for (const patch of call.selection) {
                  brush.fillRect(patch.across, patch.top, patch.wide, patch.high);
                }
              }
              if (call.shadow) {
                writeLines(call.lines, call.shadow.across, call.shadow.down,
                    call.shadow.colour);
              }
              writeLines(call.lines, 0, 0, null);
              if (call.caret) {
                brush.fillStyle = call.caret.colour;
                brush.fillRect(call.caret.across, call.caret.top,
                    call.caret.wide, call.caret.high);
              }
            }

            function writeLines(lines, movedAcross, movedDown, everyPieceIn) {
              for (const line of lines) {
                for (const piece of line.pieces) {
                  brush.font = fontOf(piece);
                  brush.fillStyle = everyPieceIn || piece.colour;
                  brush.fillText(piece.text, piece.across + movedAcross,
                      piece.baseline + movedDown);
                }
              }
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
            pictures.addEventListener('open', windowSize);

            document.fonts.load('12px "Jebol Carried"').then(() => {
              if (lastPicture) paint(lastPicture);
            });

            const keysWithoutCharacters = {
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

            function pointerLocation(kind, pointer) {
              dispatch({ type: kind, across: Math.round(pointer.offsetX), down: Math.round(pointer.offsetY) });
            }

            function characterTyped(pressed) {
              if (pressed.key in keysThatTypeAControlCharacter) {
                return keysThatTypeAControlCharacter[pressed.key];
              }
              return [...pressed.key].length === 1 ? pressed.key.codePointAt(0) : null;
            }

            function dispatchKey(pressed, typing, naming) {
              if (pressed.key in keysWithoutCharacters) {
                dispatch({ type: naming, named: keysWithoutCharacters[pressed.key] });
                return;
              }
              const typed = characterTyped(pressed);
              if (typed !== null) {
                dispatch({ type: typing, code: typed });
              }
            }

            window.addEventListener('resize', windowSize);
            canvas.addEventListener('mousedown', pointer => pointerLocation('down', pointer));
            canvas.addEventListener('mouseup', pointer => pointerLocation('up', pointer));
            canvas.addEventListener('mousemove', pointer => pointerLocation('move', pointer));
            window.addEventListener('keydown', pressed => dispatchKey(pressed, 'key', 'control'));
            window.addEventListener('keyup', pressed => dispatchKey(pressed, 'key-up', 'control-up'));
            window.addEventListener('beforeunload', () => dispatch({ type: 'close' }));

            windowSize();
            </script>
            </body>
            </html>
            """;
}
