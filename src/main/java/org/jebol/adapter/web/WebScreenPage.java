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
            <script src="/jebol-screen.js"></script>
            <script src="/jebol-input.js"></script>
            <script src="/jebol-served-page.js"></script>
            </body>
            </html>
            """;
}
