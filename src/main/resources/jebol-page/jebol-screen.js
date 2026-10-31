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
}
