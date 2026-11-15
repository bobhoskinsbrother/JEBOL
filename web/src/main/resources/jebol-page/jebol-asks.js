class JebolAsks {

  constructor(page) {
    this.page = page;
    this.typingHeld = false;
    this.savedHandles = new Map();
    this.midiAccess = null;
    this.midiConnections = new Map();
    this.midiConnectionsMade = 0;
    this.prompt = null;
    this.typing = null;
  }

  take(ask) {
    const kind = 'answer' + ask.kind.split('-').map(word => word[0].toUpperCase() + word.slice(1)).join('');
    if (typeof this[kind] !== 'function') {
      this.refuse(ask, 'this page has no answer for ' + ask.kind);
      return;
    }
    Promise.resolve()
        .then(() => this[kind](ask))
        .catch(problem => this.refuse(ask, String(problem && problem.message || problem)));
  }

  isTyping() {
    return this.typingHeld;
  }

  holdTyping(held, heading) {
    this.typingHeld = held;
    const typing = this.typingInput();
    typing.setAttribute('aria-label', heading || 'Type into the form on the screen');
    if (held) {
      typing.value = '';
      typing.focus({ preventScroll: true });
    } else {
      typing.blur();
    }
  }

  give(ask, fields) {
    this.page.answer(Object.assign({ type: 'answer', ask: ask.ask, answer: 'given' }, fields || {}));
  }

  decline(ask) {
    this.page.answer({ type: 'answer', ask: ask.ask, answer: 'declined' });
  }

  refuse(ask, why) {
    this.page.answer({ type: 'answer', ask: ask.ask, answer: 'refused', why: why });
  }

  answerColour(ask) {
    const prompt = this.showPrompt('The app asks you to choose a colour.', 'Use this colour', ['colour', 'hex']);
    const suggested = ask.suggested || '#000000';
    prompt.colour.value = suggested;
    prompt.hex.value = suggested;
    prompt.onYes = () => this.give(ask, { colour: prompt.colour.value.toLowerCase() });
    prompt.onNo = () => this.decline(ask);
  }

  answerOpenFiles(ask) {
    const prompt = this.showPrompt(ask.many ? 'The app asks you to choose files.' : 'The app asks you to choose a file.',
        'Choose…', ['file']);
    prompt.file.value = '';
    prompt.file.multiple = !!ask.many;
    if (ask.accept) {
      prompt.file.setAttribute('accept', ask.accept);
    } else {
      prompt.file.removeAttribute('accept');
    }
    prompt.onYes = () => { prompt.file.click(); return 'stay'; };
    prompt.onNo = () => this.decline(ask);
    prompt.onCancel = () => this.decline(ask);
    prompt.onFiles = async chosen => {
      const files = [];
      for (const file of chosen) {
        files.push({ name: file.name, octets: this.asBase64(new Uint8Array(await file.arrayBuffer())) });
      }
      this.give(ask, { files: files });
    };
  }

  answerOpenFolder(ask) {
    const prompt = this.showPrompt('The app asks you to choose a folder.', 'Choose…', ['folder']);
    prompt.folder.value = '';
    prompt.onYes = () => { prompt.folder.click(); return 'stay'; };
    prompt.onNo = () => this.decline(ask);
    prompt.onCancel = () => this.decline(ask);
    prompt.onFiles = async chosen => {
      const files = [];
      for (const file of chosen) {
        files.push({ path: file.webkitRelativePath || file.name,
          octets: this.asBase64(new Uint8Array(await file.arrayBuffer())) });
      }
      this.give(ask, { files: files });
    };
  }

  answerSaveFile(ask) {
    const prompt = this.showPrompt('The app asks where to save ' + ask.name + '.', 'Save…', ['name']);
    prompt.name.value = ask.name;
    prompt.onNo = () => this.decline(ask);
    prompt.onYes = () => {
      const named = prompt.name.value.trim() || ask.name;
      if (typeof window.showSaveFilePicker !== 'function') {
        this.give(ask, { name: named });
        return;
      }
      window.showSaveFilePicker({ suggestedName: named }).then(handle => {
        this.savedHandles.set(handle.name, handle);
        this.give(ask, { name: handle.name });
      }, problem => {
        if (problem && problem.name === 'AbortError') {
          this.decline(ask);
        } else {
          this.refuse(ask, 'the browser refused its save picker: ' + (problem && problem.message || problem));
        }
      });
    };
  }

  async answerSendSavedFile(ask) {
    const octets = this.fromBase64(ask.octets || '');
    const handle = this.savedHandles.get(ask.name);
    if (handle) {
      const writing = await handle.createWritable();
      await writing.write(octets);
      await writing.close();
      this.give(ask);
      return;
    }
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([octets]));
    link.download = ask.name;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    setTimeout(() => { URL.revokeObjectURL(link.href); link.remove(); }, 60000);
    this.give(ask);
  }

  answerOpenTab(ask) {
    const shown = ask.target || ask.name;
    const prompt = this.showPrompt('The app asks to open ' + shown + ' in a new tab.', 'Open', []);
    prompt.onNo = () => this.decline(ask);
    prompt.onYes = () => {
      const address = ask.target
          || URL.createObjectURL(new Blob([this.fromBase64(ask.octets || '')], { type: this.mediaTypeFor(ask.name) }));
      const opened = window.open(address, '_blank');
      if (opened) {
        opened.opener = null;
        this.give(ask);
      } else {
        this.refuse(ask, 'the browser blocked the new window');
      }
    };
  }

  answerClipboardRead(ask) {
    if (!navigator.clipboard || typeof navigator.clipboard.readText !== 'function') {
      this.refuse(ask, 'this browser has no clipboard to read');
      return;
    }
    const prompt = this.showPrompt('The app asks to read what you copied.', 'Paste', []);
    prompt.onNo = () => this.decline(ask);
    prompt.onYes = () => {
      navigator.clipboard.readText().then(text => this.give(ask, { text: text }),
          problem => this.declineOrRefuse(ask, problem));
    };
  }

  answerClipboardWrite(ask) {
    if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') {
      this.refuse(ask, 'this browser has no clipboard to write');
      return;
    }
    navigator.clipboard.writeText(ask.text).then(() => this.give(ask), () => {
      const prompt = this.showPrompt('The app asks to put text where you can paste it.', 'Copy', []);
      prompt.onNo = () => this.decline(ask);
      prompt.onYes = () => {
        navigator.clipboard.writeText(ask.text).then(() => this.give(ask),
            problem => this.declineOrRefuse(ask, problem));
      };
    });
  }

  declineOrRefuse(ask, problem) {
    if (problem && (problem.name === 'NotAllowedError' || problem.name === 'SecurityError')) {
      this.decline(ask);
    } else {
      this.refuse(ask, String(problem && problem.message || problem));
    }
  }

  async answerMidiPorts(ask) {
    const access = await this.reachMidi(ask);
    if (!access) {
      return;
    }
    this.give(ask, {
      inputs: [...access.inputs.values()].map(port => port.name),
      outputs: [...access.outputs.values()].map(port => port.name)
    });
  }

  async answerMidiOpen(ask) {
    const access = await this.reachMidi(ask);
    if (!access) {
      return;
    }
    const input = ask.input > 0 ? [...access.inputs.values()][ask.input - 1] : null;
    const output = ask.output > 0 ? [...access.outputs.values()][ask.output - 1] : null;
    if ((ask.input > 0 && !input) || (ask.output > 0 && !output)) {
      this.refuse(ask, 'the browser lists no such MIDI device');
      return;
    }
    const connection = ++this.midiConnectionsMade;
    if (input) {
      input.onmidimessage = message => this.page.tell({
        type: 'midi-arrived', connection: connection, octets: this.asBase64(message.data) });
      await input.open();
    }
    if (output) {
      await output.open();
    }
    this.midiConnections.set(connection, { input: input, output: output });
    this.give(ask, { connection: connection });
  }

  answerMidiSend(ask) {
    const held = this.midiConnections.get(ask.connection);
    if (!held || !held.output) {
      this.refuse(ask, 'that MIDI connection has no output');
      return;
    }
    held.output.send(this.fromBase64(ask.octets || ''));
    this.give(ask);
  }

  async answerMidiClose(ask) {
    const held = this.midiConnections.get(ask.connection);
    this.midiConnections.delete(ask.connection);
    if (held && held.input) {
      held.input.onmidimessage = null;
      await held.input.close();
    }
    if (held && held.output) {
      await held.output.close();
    }
    this.give(ask);
  }

  async reachMidi(ask) {
    if (typeof navigator.requestMIDIAccess !== 'function') {
      this.refuse(ask, 'this browser has no Web MIDI');
      return null;
    }
    if (!this.midiAccess) {
      try {
        this.midiAccess = await navigator.requestMIDIAccess({ sysex: false });
      } catch (problem) {
        this.decline(ask);
        return null;
      }
    }
    return this.midiAccess;
  }

  showPrompt(sentence, yesLabel, controls) {
    const prompt = this.promptElements();
    prompt.text.textContent = sentence;
    prompt.yes.textContent = yesLabel;
    for (const name of ['colour', 'hex', 'file', 'folder', 'name']) {
      prompt.rows[name].style.display = controls.includes(name) ? '' : 'none';
    }
    prompt.onYes = () => {};
    prompt.onNo = () => {};
    prompt.onCancel = () => {};
    prompt.onFiles = () => {};
    prompt.box.style.display = 'block';
    return prompt;
  }

  hidePrompt() {
    if (this.prompt) {
      this.prompt.box.style.display = 'none';
    }
  }

  promptElements() {
    if (this.prompt) {
      return this.prompt;
    }
    const box = document.createElement('div');
    box.id = 'jebol-ask';
    box.setAttribute('role', 'dialog');
    box.style.cssText = 'display:none;position:fixed;z-index:10;top:16px;left:50%;transform:translateX(-50%);'
        + 'max-width:min(440px,calc(100vw - 32px));padding:16px;border-radius:8px;background:#f4f5f7;color:#16181d;'
        + 'font:14px system-ui,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,.35)';
    const text = document.createElement('p');
    text.id = 'jebol-ask-text';
    text.style.cssText = 'margin:0 0 12px';
    box.appendChild(text);
    const rows = {};
    const colour = this.addControl(box, rows, 'colour', 'input', { type: 'color' });
    const hex = this.addControl(box, rows, 'hex', 'input', { type: 'text', spellcheck: false, autocomplete: 'off' });
    const file = this.addControl(box, rows, 'file', 'input', { type: 'file' });
    const folder = this.addControl(box, rows, 'folder', 'input', { type: 'file' });
    folder.webkitdirectory = true;
    folder.setAttribute('webkitdirectory', '');
    file.style.display = 'none';
    folder.style.display = 'none';
    const name = this.addControl(box, rows, 'name', 'input', { type: 'text', spellcheck: false, autocomplete: 'off' });
    const buttons = document.createElement('div');
    buttons.style.cssText = 'display:flex;gap:8px;justify-content:flex-end;margin-top:12px';
    const no = document.createElement('button');
    no.id = 'jebol-ask-no';
    no.textContent = 'Not now';
    const yes = document.createElement('button');
    yes.id = 'jebol-ask-yes';
    buttons.appendChild(no);
    buttons.appendChild(yes);
    box.appendChild(buttons);
    document.body.appendChild(box);
    const prompt = { box, text, rows, colour, hex, file, folder, name, yes, no };
    colour.addEventListener('input', () => { hex.value = colour.value; });
    hex.addEventListener('input', () => {
      if (/^#[0-9a-fA-F]{6}$/.test(hex.value.trim())) {
        colour.value = hex.value.trim().toLowerCase();
      }
    });
    yes.addEventListener('click', () => {
      if (prompt.onYes() !== 'stay') {
        this.hidePrompt();
      }
    });
    no.addEventListener('click', () => { this.hidePrompt(); prompt.onNo(); });
    for (const chooser of [file, folder]) {
      chooser.addEventListener('change', () => {
        const chosen = [...chooser.files];
        this.hidePrompt();
        if (chosen.length === 0) {
          prompt.onCancel();
        } else {
          prompt.onFiles(chosen);
        }
      });
      chooser.addEventListener('cancel', () => { this.hidePrompt(); prompt.onCancel(); });
    }
    this.prompt = prompt;
    return prompt;
  }

  addControl(box, rows, name, tag, attributes) {
    const row = document.createElement('div');
    row.style.cssText = 'margin:6px 0';
    const control = document.createElement(tag);
    control.id = 'jebol-ask-' + name;
    for (const [attribute, value] of Object.entries(attributes)) {
      control.setAttribute(attribute, value);
    }
    row.appendChild(control);
    box.appendChild(row);
    rows[name] = row;
    return control;
  }

  typingInput() {
    if (this.typing) {
      return this.typing;
    }
    const typing = document.createElement('input');
    typing.id = 'jebol-typing';
    typing.type = 'text';
    typing.setAttribute('autocomplete', 'off');
    typing.setAttribute('autocapitalize', 'off');
    typing.setAttribute('spellcheck', 'false');
    typing.setAttribute('aria-label', 'Type into the form on the screen');
    typing.style.cssText = 'position:fixed;left:0;bottom:0;width:1px;height:1px;opacity:0;border:0;padding:0;'
        + 'font-size:16px;z-index:-1';
    const controlCharacters = { Backspace: 8, Tab: 9, Enter: 13, Escape: 27, Delete: 127 };
    typing.addEventListener('keydown', pressed => {
      pressed.stopPropagation();
      if (pressed.isComposing) {
        return;
      }
      if (pressed.key in controlCharacters) {
        pressed.preventDefault();
        this.page.key({ type: 'key', code: controlCharacters[pressed.key] });
      }
    });
    typing.addEventListener('keyup', pressed => pressed.stopPropagation());
    typing.addEventListener('input', changed => {
      if (!changed.isComposing) {
        this.sendTyped(typing);
      }
    });
    typing.addEventListener('compositionend', () => this.sendTyped(typing));
    typing.addEventListener('blur', () => {
      if (this.typingHeld) {
        setTimeout(() => typing.focus({ preventScroll: true }), 0);
      }
    });
    document.body.appendChild(typing);
    this.typing = typing;
    return typing;
  }

  sendTyped(typing) {
    for (const character of typing.value) {
      this.page.key({ type: 'key', code: character.codePointAt(0) });
    }
    typing.value = '';
  }

  mediaTypeFor(name) {
    const suffix = String(name || '').toLowerCase().split('.').pop();
    return { html: 'text/html', htm: 'text/html', txt: 'text/plain', svg: 'image/svg+xml', png: 'image/png',
      jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', pdf: 'application/pdf', json: 'application/json'
    }[suffix] || 'application/octet-stream';
  }

  asBase64(octets) {
    let binary = '';
    for (let at = 0; at < octets.length; at += 0x8000) {
      binary += String.fromCharCode.apply(null, octets.subarray(at, at + 0x8000));
    }
    return btoa(binary);
  }

  fromBase64(written) {
    const binary = atob(written);
    const octets = new Uint8Array(binary.length);
    for (let at = 0; at < binary.length; at++) {
      octets[at] = binary.charCodeAt(at);
    }
    return octets;
  }
}
