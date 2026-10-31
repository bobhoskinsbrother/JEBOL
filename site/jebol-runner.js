(function () {
  const canvas = document.getElementById('screen');
  const screen = new JebolScreen(canvas);
  const loading = new JebolLoading(document.getElementById('loading'));
  const pageOpenedAt = performance.now();
  const app = fetch('app.json').then(response => response.json());

  const printed = [];
  const output = document.createElement('pre');
  output.id = 'out';
  output.textContent = '';

  function print(text) {
    printed.push(text);
    output.textContent = printed.join('');
    output.scrollTop = output.scrollHeight;
  }

  const worker = new Worker('jebol-worker.js');

  function postToWorker(fields) {
    worker.postMessage(fields);
  }

  function storeAppFiles(names) {
    return Promise.all(names.map(name =>
        fetch(name).then(response => response.text()).then(text =>
            postToWorker({ type: 'file', path: name, text: text }))));
  }

  function runTheApp() {
    app.then(described => storeAppFiles(described.files)
        .then(() => fetch(described.entry))
        .then(response => response.text())
        .then(source => postToWorker({ run: source })));
  }

  const asks = new JebolAsks({ answer: postToWorker, tell: postToWorker, key: postToWorker });
  const navigation = new JebolNavigation({ tell: postToWorker, tellAsThePageGoes: postToWorker });

  function hearWorker(message) {
    const said = typeof message.data === 'string' ? JSON.parse(message.data) : message.data;
    if (said.type === 'paint') {
      loading.finish();
      screen.paint(said.picture);
    } else if (said.type === 'downloading') {
      loading.reportDownload(said.received, said.total, said.done);
    } else if (said.type === 'ask') {
      asks.take(said);
    } else if (said.type === 'typing') {
      asks.holdTyping(said.held, said.heading);
    } else if (said.type === 'navigate' || said.type === 'guard' || said.type === 'title') {
      navigation.take(said);
    } else if (said.type === 'printed') {
      print(said.text);
    } else if (said.type === 'booted') {
      print('booted in ' + Math.round(performance.now() - pageOpenedAt) + ' ms\n');
      loading.say('Starting the app');
      runTheApp();
    } else if (said.type === 'loaded') {
      print('worker loaded in ' + said.loadMs + ' ms and started the interpreter in '
          + said.mainMs + ' ms\n');
    } else if (said.type === 'finished') {
      loading.finish();
      print(said.text);
    } else if (said.type === 'failed') {
      loading.fail('JEBOL could not start: ' + said.text);
      print('failed ' + said.text);
    }
  }

  worker.onmessage = hearWorker;
  worker.onerror = problem => {
    loading.fail('JEBOL could not start: ' + problem.message);
    print('worker error ' + problem.message);
  };
  document.body.appendChild(output);
  navigation.arrive();

  const input = new JebolInput(canvas, postToWorker);
  input.reportViewportSize();
})();
