class JebolInput {
  constructor(canvas, report) {
    this.report = report;
    this.turned = 0;
    window.addEventListener('resize', () => this.reportViewportSize());
    canvas.addEventListener('mousedown', pointer => this.reportPointer('down', pointer));
    canvas.addEventListener('mouseup', pointer => this.reportPointer('up', pointer));
    canvas.addEventListener('mousemove', pointer => this.reportPointer('move', pointer));
    canvas.addEventListener('wheel', wheel => this.reportWheel(wheel), { passive: false });
    window.addEventListener('keydown', pressed => this.reportKey(pressed, 'key', 'control'));
    window.addEventListener('keyup', pressed => this.reportKey(pressed, 'key-up', 'control-up'));
  }

  reportViewportSize() {
    this.report({ type: 'measure', wide: window.innerWidth, high: window.innerHeight });
  }

  reportPointer(kind, pointer) {
    this.report({ type: kind, across: Math.round(pointer.offsetX), down: Math.round(pointer.offsetY) });
  }

  static countNotches(wheel) {
    if (wheel.deltaMode === 1) {
      return wheel.deltaY / JebolInput.linesPerNotch;
    }
    if (wheel.deltaMode === 2) {
      return wheel.deltaY;
    }
    return wheel.deltaY / JebolInput.pixelsPerNotch;
  }

  reportWheel(wheel) {
    wheel.preventDefault();
    this.turned += JebolInput.countNotches(wheel);
    const whole = Math.trunc(this.turned);
    if (whole === 0) {
      return;
    }
    this.turned -= whole;
    const wholePages = wheel.ctrlKey || wheel.deltaMode === 2;
    this.report({
      type: wholePages ? 'scroll-page' : 'scroll-line',
      across: Math.round(wheel.offsetX),
      down: Math.round(wheel.offsetY),
      amount: -whole * (wholePages ? JebolInput.pageScroll : JebolInput.lineScroll)
    });
  }

  static typedCodePoint(pressed) {
    if (pressed.key in JebolInput.controlCharacters) {
      return JebolInput.controlCharacters[pressed.key];
    }
    return [...pressed.key].length === 1 ? pressed.key.codePointAt(0) : null;
  }

  reportKey(pressed, typing, naming) {
    if (pressed.key in JebolInput.namedKeys) {
      this.report({ type: naming, named: JebolInput.namedKeys[pressed.key] });
      return;
    }
    const typed = JebolInput.typedCodePoint(pressed);
    if (typed !== null) {
      this.report({ type: typing, code: typed });
    }
  }
}

JebolInput.namedKeys = {
  PageUp: 'page-up', PageDown: 'page-down', End: 'end', Home: 'home',
  ArrowLeft: 'left', ArrowUp: 'up', ArrowRight: 'right', ArrowDown: 'down',
  Insert: 'insert',
  F1: 'f1', F2: 'f2', F3: 'f3', F4: 'f4', F5: 'f5', F6: 'f6',
  F7: 'f7', F8: 'f8', F9: 'f9', F10: 'f10', F11: 'f11', F12: 'f12',
  Shift: 'shift', Control: 'control', Alt: 'alt', Pause: 'pause', CapsLock: 'capital'
};
JebolInput.controlCharacters = { Backspace: 8, Tab: 9, Enter: 13, Escape: 27, Delete: 127 };
JebolInput.pixelsPerNotch = 100;
JebolInput.linesPerNotch = 3;
JebolInput.lineScroll = 30;
JebolInput.pageScroll = 10;
