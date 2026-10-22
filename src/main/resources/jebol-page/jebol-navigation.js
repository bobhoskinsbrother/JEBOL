class JebolNavigation {

  constructor(page) {
    this.page = page;
    this.unsavedWork = false;
    this.index = this.indexKeptBy(history.state) ?? 0;
    history.replaceState(this.stateFor(this.index), '', location.href);
    window.addEventListener('popstate', () => this.reportMove());
    window.addEventListener('beforeunload', leaving => this.askBeforeLeaving(leaving));
    window.addEventListener('pagehide', () => this.page.tellAsThePageGoes({ type: 'close' }));
    window.addEventListener('pageshow', shown => this.restartIfRestored(shown));
  }

  arrive() {
    this.page.tell({ type: 'arrive', index: this.index, address: this.address() });
  }

  take(said) {
    if (said.type === 'navigate') {
      this.move(said);
    } else if (said.type === 'guard') {
      this.unsavedWork = said.unsaved === true;
    } else if (said.type === 'title') {
      document.title = said.text;
    }
  }

  move(said) {
    if (said.move === 'push') {
      history.pushState(this.stateFor(said.index), '', this.urlFor(said.address));
      this.index = said.index;
    } else if (said.move === 'replace') {
      history.replaceState(this.stateFor(said.index), '', this.urlFor(said.address));
      this.index = said.index;
    } else if (said.move === 'back') {
      history.back();
    } else if (said.move === 'forward') {
      history.forward();
    }
  }

  reportMove() {
    const kept = this.indexKeptBy(history.state);
    if (kept === null) {
      this.index += 1;
      history.replaceState(this.stateFor(this.index), '', location.href);
    } else {
      this.index = kept;
    }
    this.page.tell({ type: 'moved', index: this.index, address: this.address() });
  }

  askBeforeLeaving(leaving) {
    if (this.unsavedWork) {
      leaving.preventDefault();
      leaving.returnValue = '';
    }
  }

  restartIfRestored(shown) {
    if (shown.persisted) {
      location.reload();
    }
  }

  address() {
    return location.hash.startsWith('#') ? location.hash.slice(1) : '';
  }

  urlFor(address) {
    return address === '' ? location.pathname + location.search : '#' + address;
  }

  stateFor(index) {
    return { jebolIndex: index };
  }

  indexKeptBy(state) {
    return state && Number.isInteger(state.jebolIndex) && state.jebolIndex >= 0 ? state.jebolIndex : null;
  }
}
