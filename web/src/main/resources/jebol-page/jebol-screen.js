class JebolScreen {
  constructor(canvas) {
    this.canvas = canvas;
    this.brush = canvas.getContext('2d');
    this.painter = new JebolPainter(this.brush);
    this.lastPicture = null;
    document.fonts.load('12px "Jebol Carried"').then(() => {
      if (this.lastPicture) this.paint(this.lastPicture);
    });
  }

  paint(picture) {
    this.lastPicture = picture;
    this.canvas.width = picture.wide;
    this.canvas.height = picture.high;
    this.brush.clearRect(0, 0, picture.wide, picture.high);
    this.painter.paintCalls(picture.paint);
  }

  useCursor(said) {
    this.canvas.style.cursor = said.rgba ? this.cursorFromPixels(said) : said.css;
  }

  cursorFromPixels(said) {
    const picture = document.createElement('canvas');
    picture.width = said.wide;
    picture.height = said.high;
    const octets = Uint8ClampedArray.from(atob(said.rgba), character => character.charCodeAt(0));
    picture.getContext('2d').putImageData(new ImageData(octets, said.wide, said.high), 0, 0);
    return 'url(' + picture.toDataURL() + ') 0 0, auto';
  }
}
