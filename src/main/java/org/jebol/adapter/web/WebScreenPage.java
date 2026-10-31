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
            <script src="/jebol-asks.js"></script>
            <script src="/jebol-navigation.js"></script>
            <script src="/jebol-paint.js"></script>
            <script>
            const canvas = document.getElementById('screen');
            const brush = canvas.getContext('2d');
            const painter = new JebolPainter(brush);

            let eventResponse = Promise.resolve();

            function dispatch(fields) {
              eventResponse = eventResponse
                  .then(() => fetch('/event', { method: 'POST', body: JSON.stringify(fields) }))
                  .catch(() => {});
            }

            const asks = new JebolAsks({ answer: dispatch, tell: dispatch, key: dispatch });

            function sendAsThePageGoes(fields) {
              navigator.sendBeacon('/event', JSON.stringify(fields));
            }

            const navigation = new JebolNavigation({ tell: dispatch, tellAsThePageGoes: sendAsThePageGoes });
            navigation.arrive();

            function reportViewportSize() {
              dispatch({ type: 'measure', wide: window.innerWidth, high: window.innerHeight });
            }

            let lastPicture = null;

            function paint(picture) {
              lastPicture = picture;
              canvas.width = picture.wide;
              canvas.height = picture.high;
              brush.clearRect(0, 0, picture.wide, picture.high);
              painter.paintCalls(picture.paint);
            }

            function openPictures() {
              const pictures = new EventSource('/paint');
              pictures.addEventListener('paint', message => paint(JSON.parse(message.data)));
              pictures.addEventListener('open', reportViewportSize);
              pictures.addEventListener('ask', message => asks.take(JSON.parse(message.data)));
              pictures.addEventListener('typing', message => {
                const said = JSON.parse(message.data);
                asks.holdTyping(said.held, said.heading);
              });
              for (const kind of ['navigate', 'guard', 'title']) {
                pictures.addEventListener(kind, message => navigation.take(JSON.parse(message.data)));
              }
            }

            eventResponse.then(openPictures);

            document.fonts.load('12px "Jebol Carried"').then(() => {
              if (lastPicture) paint(lastPicture);
            });

            const namedKeys = {
              PageUp: 'page-up', PageDown: 'page-down', End: 'end', Home: 'home',
              ArrowLeft: 'left', ArrowUp: 'up', ArrowRight: 'right', ArrowDown: 'down',
              Insert: 'insert',
              F1: 'f1', F2: 'f2', F3: 'f3', F4: 'f4', F5: 'f5', F6: 'f6',
              F7: 'f7', F8: 'f8', F9: 'f9', F10: 'f10', F11: 'f11', F12: 'f12',
              Shift: 'shift', Control: 'control', Alt: 'alt', Pause: 'pause', CapsLock: 'capital'
            };
            const controlCharacters = {
              Backspace: 8, Tab: 9, Enter: 13, Escape: 27, Delete: 127
            };

            function reportPointer(kind, pointer) {
              dispatch({ type: kind, across: Math.round(pointer.offsetX), down: Math.round(pointer.offsetY) });
            }

            const pixelsPerNotch = 100;
            const linesPerNotch = 3;
            const lineScroll = 30;
            const pageScroll = 10;

            let turned = 0;

            function countNotches(wheel) {
              if (wheel.deltaMode === 1) {
                return wheel.deltaY / linesPerNotch;
              }
              if (wheel.deltaMode === 2) {
                return wheel.deltaY;
              }
              return wheel.deltaY / pixelsPerNotch;
            }

            function reportWheel(wheel) {
              wheel.preventDefault();
              turned += countNotches(wheel);
              const whole = Math.trunc(turned);
              if (whole === 0) {
                return;
              }
              turned -= whole;
              const wholePages = wheel.ctrlKey || wheel.deltaMode === 2;
              dispatch({
                type: wholePages ? 'scroll-page' : 'scroll-line',
                across: Math.round(wheel.offsetX),
                down: Math.round(wheel.offsetY),
                amount: -whole * (wholePages ? pageScroll : lineScroll)
              });
            }

            function typedCodePoint(pressed) {
              if (pressed.key in controlCharacters) {
                return controlCharacters[pressed.key];
              }
              return [...pressed.key].length === 1 ? pressed.key.codePointAt(0) : null;
            }

            function reportKey(pressed, typing, naming) {
              if (pressed.key in namedKeys) {
                dispatch({ type: naming, named: namedKeys[pressed.key] });
                return;
              }
              const typed = typedCodePoint(pressed);
              if (typed !== null) {
                dispatch({ type: typing, code: typed });
              }
            }

            window.addEventListener('resize', reportViewportSize);
            canvas.addEventListener('mousedown', pointer => reportPointer('down', pointer));
            canvas.addEventListener('mouseup', pointer => reportPointer('up', pointer));
            canvas.addEventListener('mousemove', pointer => reportPointer('move', pointer));
            canvas.addEventListener('wheel', reportWheel, { passive: false });
            window.addEventListener('keydown', pressed => reportKey(pressed, 'key', 'control'));
            window.addEventListener('keyup', pressed => reportKey(pressed, 'key-up', 'control-up'));

            reportViewportSize();
            </script>
            </body>
            </html>
            """;
}
