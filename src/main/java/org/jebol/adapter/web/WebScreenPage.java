package org.jebol.adapter.web;

/**
 * The page a browser loads, and the whole of what runs in it.
 *
 * <p>Two dozen lines of script, and they execute a paint list. They add up no
 * offsets, work out no clip and multiply no opacity, because every one of
 * those was decided once in {@code PaintList}. That is the whole reason a page
 * and a desktop window show the same picture, and it is why this file is
 * short: a browser renderer that needed to be clever would be a browser
 * renderer that could disagree.
 *
 * <p>Held as a string rather than a resource file so that the jar has one
 * fewer thing in it and the page cannot go missing.
 */
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
                brush.globalAlpha = step.opacity / 255;
                if (step.kind === 'fill') {
                  brush.fillStyle = step.colour;
                  brush.fillRect(step.across, step.down, step.wide, step.high);
                } else if (step.kind === 'writing') {
                  brush.fillStyle = step.colour;
                  brush.font = '12px sans-serif';
                  brush.fillText(step.text, step.across + 2,
                      step.down + Math.min(step.high - 2, 14));
                } else if (step.kind === 'picture') {
                  brush.putImageData(asImageData(step), step.across, step.down);
                }
                brush.restore();
              }
            }

            function asImageData(step) {
              const raw = atob(step.pixels);
              const octets = new Uint8ClampedArray(raw.length);
              for (let at = 0; at < raw.length; at++) octets[at] = raw.charCodeAt(at);
              return new ImageData(octets, step.wide, step.high);
            }

            const pictures = new EventSource('/paint');
            pictures.addEventListener('paint', message => paint(JSON.parse(message.data)));
            pictures.addEventListener('open', sayHowBigWeAre);

            window.addEventListener('resize', sayHowBigWeAre);
            canvas.addEventListener('mousedown', () => tell({ kind: 'down' }));
            canvas.addEventListener('mouseup', () => tell({ kind: 'up' }));
            window.addEventListener('keydown', () => tell({ kind: 'key' }));
            window.addEventListener('keyup', () => tell({ kind: 'key-up' }));
            window.addEventListener('beforeunload', () => tell({ kind: 'close' }));

            sayHowBigWeAre();
            </script>
            </body>
            </html>
            """;
}
