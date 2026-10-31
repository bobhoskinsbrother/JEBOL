class JebolLoading {
  constructor(element) {
    this.element = element;
    this.saying = element.querySelector('.jebol-loading-says');
    this.isOver = false;
  }

  say(text) {
    if (!this.isOver) {
      this.saying.textContent = text;
    }
  }

  reportDownload(received, total, done) {
    const megabytes = bytes => (bytes / (1024 * 1024)).toFixed(1);
    if (done) {
      this.say('Starting JEBOL');
    } else if (total > 0) {
      this.say('Downloading ' + megabytes(received) + ' of ' + megabytes(total) + ' MB');
    } else {
      this.say('Downloading ' + megabytes(received) + ' MB');
    }
  }

  fail(text) {
    this.say(text);
    this.element.classList.add('jebol-loading-failed');
  }

  finish() {
    if (!this.isOver) {
      this.isOver = true;
      this.element.remove();
    }
  }
}
