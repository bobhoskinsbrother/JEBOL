class JebolPainter {
  constructor(brush) {
    this.brush = brush;
  }

  paintCalls(calls) {
    for (const call of calls) {
      this.paintOneCall(call);
    }
  }

  paintOneCall(call) {
    const brush = this.brush;
    brush.save();
    brush.beginPath();
    brush.rect(call.clip.across, call.clip.down, call.clip.wide, call.clip.high);
    brush.clip();
    if (call['clip-shape'] && call['clip-shape'].length) {
      this.clipToShape(call);
    }
    brush.globalAlpha = call.opacity / 255;
    if (call.type === 'fill') {
      this.fillPath(call);
    } else if (call.type === 'stroke') {
      this.strokePath(call);
    } else if (call.type === 'picture') {
      this.showPicture(call);
    } else if (call.type === 'text') {
      this.writeText(call);
    }
    brush.restore();
  }

  static isIdentity(t) {
    return !t || (t[0] === 1 && t[1] === 0 && t[2] === 0
        && t[3] === 1 && t[4] === 0 && t[5] === 0);
  }

  clipToShape(call) {
    const inside = JebolPainter.asPath(call['clip-shape']);
    if (JebolPainter.isIdentity(call.transform)) {
      this.brush.clip(inside);
      return;
    }
    const t = call.transform;
    const placed = new Path2D();
    placed.addPath(inside, new DOMMatrix([t[0], t[1], t[2], t[3], t[4], t[5]]));
    this.brush.clip(placed);
  }

  applyTransform(call) {
    const t = call.transform;
    if (!JebolPainter.isIdentity(t)) this.brush.transform(t[0], t[1], t[2], t[3], t[4], t[5]);
  }

  fillPath(call) {
    this.applyTransform(call);
    this.brush.fillStyle = call.gradient ? this.asGradient(call.gradient) : call.colour;
    this.brush.fill(JebolPainter.asPath(call.path), call.rule === 'even-odd' ? 'evenodd' : 'nonzero');
  }

  strokePath(call) {
    const brush = this.brush;
    this.applyTransform(call);
    brush.strokeStyle = call.colour;
    brush.lineWidth = call.width;
    brush.lineCap = JebolPainter.capNames[call.cap];
    brush.lineJoin = JebolPainter.joinNames[call.join];
    brush.setLineDash(call.dashes || []);
    brush.stroke(JebolPainter.asPath(call.path));
    brush.setLineDash([]);
  }

  showPicture(call) {
    this.applyTransform(call);
    const pixels = JebolPainter.asImageData(call);
    const held = new OffscreenCanvas(pixels.width, pixels.height);
    held.getContext('2d').putImageData(pixels, 0, 0);
    this.brush.drawImage(held, call.across, call.down, call['draw-wide'], call['draw-high']);
  }

  static fontFor(piece) {
    return (piece.italic ? 'italic ' : '') + (piece.bold ? 'bold ' : '')
        + piece.size + 'px "Jebol Carried", sans-serif';
  }

  writeText(call) {
    const brush = this.brush;
    this.applyTransform(call);
    if (call.selection && call.selection.length) {
      brush.fillStyle = call['selection-colour'];
      for (const patch of call.selection) {
        brush.fillRect(patch.across, patch.top, patch.wide, patch.high);
      }
    }
    if (call.shadow) {
      this.writeLines(call.lines, call.shadow.across, call.shadow.down, call.shadow.colour);
    }
    this.writeLines(call.lines, 0, 0, null);
    if (call.caret) {
      brush.fillStyle = call.caret.colour;
      brush.fillRect(call.caret.across, call.caret.top, call.caret.wide, call.caret.high);
    }
  }

  writeLines(lines, movedAcross, movedDown, colourOverride) {
    const brush = this.brush;
    for (const line of lines) {
      for (const piece of line.pieces) {
        brush.font = JebolPainter.fontFor(piece);
        brush.fillStyle = colourOverride || piece.colour;
        brush.fillText(piece.text, piece.across + movedAcross, piece.baseline + movedDown);
      }
    }
  }

  asGradient(fill) {
    const brush = this.brush;
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

  static asPath(steps) {
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

  static asImageData(step) {
    const raw = atob(step.pixels);
    const octets = new Uint8ClampedArray(raw.length);
    for (let at = 0; at < raw.length; at++) octets[at] = raw.charCodeAt(at);
    return new ImageData(octets,
        step['pixel-wide'] || step.wide, step['pixel-high'] || step.high);
  }
}

JebolPainter.capNames = { 'butt': 'butt', 'square': 'square', 'rounded': 'round' };
JebolPainter.joinNames = { 'miter': 'miter', 'miter-bevel': 'miter', 'round': 'round', 'bevel': 'bevel' };
