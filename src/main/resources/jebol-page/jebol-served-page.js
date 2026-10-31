(function () {
  const canvas = document.getElementById('screen');
  const screen = new JebolScreen(canvas);

  let eventResponse = Promise.resolve();

  function dispatch(fields) {
    eventResponse = eventResponse
        .then(() => fetch('/event', { method: 'POST', body: JSON.stringify(fields) }))
        .catch(() => {});
  }

  function sendAsThePageGoes(fields) {
    navigator.sendBeacon('/event', JSON.stringify(fields));
  }

  const asks = new JebolAsks({ answer: dispatch, tell: dispatch, key: dispatch });
  const navigation = new JebolNavigation({ tell: dispatch, tellAsThePageGoes: sendAsThePageGoes });
  navigation.arrive();

  const input = new JebolInput(canvas, dispatch);

  function openPictures() {
    const pictures = new EventSource('/paint');
    pictures.addEventListener('paint', message => screen.paint(JSON.parse(message.data)));
    pictures.addEventListener('open', () => input.reportViewportSize());
    pictures.addEventListener('ask', message => asks.take(JSON.parse(message.data)));
    pictures.addEventListener('typing', message => {
      const said = JSON.parse(message.data);
      asks.holdTyping(said.held, said.heading);
    });
    for (const kind of ['navigate', 'guard', 'title']) {
      pictures.addEventListener(kind, message => navigation.take(JSON.parse(message.data)));
    }
  }

  eventResponse.then(openPictures);
  input.reportViewportSize();
})();
