'use strict';
(() => {
  const tg = window.Telegram?.WebApp;
  const form = document.getElementById('search-form');
  const searchView = document.getElementById('search-view');
  const content = document.getElementById('content-view');
  const status = document.getElementById('status');
  const back = document.getElementById('back');
  const messages = {
    INVALID_INPUT: 'Проверьте дату, время, количество гостей и бюджет.',
    NO_RESULTS: 'Подходящих заведений не найдено. Попробуйте изменить параметры.',
    AUTH_FAILED: 'Не удалось подтвердить запуск через Telegram.',
    TEMPORARY_ERROR: 'Сервис временно недоступен. Попробуйте позже.',
    NOT_FOUND: 'Заведение не найдено.'
  };
  let cards = [];
  let warnings = [];
  let view = 'search';
  let busy = false;
  const money = value => new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 2 }).format(value);
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  const showStatus = text => { status.textContent = text; status.hidden = !text; };

  // Dates use Minsk even when the browser's timezone differs. Backend remains authoritative.
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Minsk', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const last = new Date(`${today}T12:00:00Z`);
  last.setUTCDate(last.getUTCDate() + 6);
  const date = document.getElementById('date');
  date.min = today;
  date.max = last.toISOString().slice(0, 10);
  date.value = today;
  if (!tg?.initData) {
    const note = document.getElementById('launch-note');
    note.textContent = 'Запуск вне Telegram. Поиск доступен только на явно настроенном локальном dev/test сервере.';
    note.hidden = false;
  }
  tg?.ready();
  tg?.expand();

  function setView(next) {
    view = next;
    searchView.hidden = next !== 'search';
    content.hidden = next === 'search';
    content.setAttribute('aria-label', next === 'details' ? 'Подробности заведения' : next === 'menu' ? 'Сохранённое меню' : 'Результаты поиска');
    back.hidden = next === 'search';
    if (next === 'search') tg?.BackButton?.hide();
    else tg?.BackButton?.show();
    window.scrollTo(0, 0);
  }

  async function api(path, body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), path === '/search/natural' ? 30000 : 15000);
    try {
      const response = await fetch(`/api/miniapp/v1${path}`, {
        method: body ? 'POST' : 'GET', credentials: 'omit', signal: controller.signal,
        headers: { 'X-Telegram-Init-Data': tg?.initData || '', ...(body ? { 'Content-Type': 'application/json' } : {}) },
        ...(body ? { body: JSON.stringify(body) } : {})
      });
      const data = await response.json();
      if (!response.ok) throw new Error(messages[data.code] ? data.code : 'TEMPORARY_ERROR');
      return data;
    } finally { clearTimeout(timeout); }
  }

  async function load(action) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button').forEach(b => { b.disabled = true; });
    content.setAttribute('aria-busy', 'true');
    showStatus('Загружаем…');
    try { showStatus(await action() || ''); }
    catch (error) { showStatus(messages[error.message] || messages.TEMPORARY_ERROR); }
    finally {
      busy = false;
      content.removeAttribute('aria-busy');
      document.querySelectorAll('button').forEach(b => { b.disabled = false; });
    }
  }

  function actionButton(text, action, secondary = false) {
    const button = node('button', text, secondary ? 'secondary' : undefined);
    button.type = 'button';
    button.addEventListener('click', () => load(action));
    return button;
  }

  function results() {
    content.replaceChildren(node('h2', 'Варианты для вас'));
    if (!cards.length) content.append(node('p', messages.NO_RESULTS, 'notice'));
    for (const card of cards) {
      const article = node('article', undefined, 'card');
      article.append(node('h3', card.name), node('p', card.cuisines.join(' · '), 'muted'),
        node('p', card.address), node('p', `≈ ${money(card.estimatedTotalByn)} BYN на всех гостей`, 'price'),
        node('p', card.openingSummary, 'opening'));
      const reasons = node('ul', undefined, 'reasons');
      card.reasons.forEach(reason => reasons.append(node('li', reason)));
      const actions = node('div', undefined, 'actions');
      actions.append(actionButton('Подробнее', () => details(card)), actionButton('Меню', () => menu(card), true));
      article.append(reasons, actions);
      content.append(article);
    }
    warnings.forEach(warning => content.append(node('p', warning, 'hint')));
    setView('results');
  }

  async function details(card) {
    const data = await api(`/restaurants/${card.id}`);
    content.replaceChildren(node('h2', data.name), node('p', data.address),
      node('p', data.cuisines.join(' · '), 'muted'),
      node('p', data.estimatedCheckPerGuestByn == null ? 'Ориентир чека недоступен'
        : `≈ ${money(data.estimatedCheckPerGuestByn)} BYN на гостя`, 'price'),
      node('p', 'Ориентировочный чек, не гарантия итоговой суммы.', 'hint'), node('h3', 'Недельное расписание'));
    if (!data.openingInformation.length) content.append(node('p', 'Расписание пока недоступно.'));
    data.openingInformation.forEach(line => content.append(node('p', line)));
    content.append(node('p', 'Праздничные часы могут отличаться.', 'hint'), actionButton('Открыть меню', () => menu(card)));
    const evidence = node('section', undefined, 'evidence');
    evidence.append(node('p', 'Источники и проверка'));
    data.evidence.forEach(item => {
      const row = node('p');
      try {
        const url = new URL(item.url);
        if (url.protocol === 'https:' || url.protocol === 'http:') {
          const link = node('a', item.label);
          link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer';
          row.append(link);
        } else row.append(node('span', item.label));
      } catch { row.append(node('span', item.label)); }
      if (item.verifiedAt) row.append(document.createTextNode(` · проверено ${item.verifiedAt}`));
      evidence.append(row);
    });
    content.append(evidence);
    setView('details');
  }

  async function menu(card) {
    const data = await api(`/restaurants/${card.id}/menu`);
    content.replaceChildren(node('h2', `Меню · ${card.name}`), node('p', data.notice, 'notice'));
    data.items.forEach(item => {
      const row = node('article', undefined, 'menu-item');
      row.append(node('strong', item.name), node('span', `${money(item.priceByn)} BYN${item.portion ? ` · ${item.portion}` : ''}`));
      content.append(row);
    });
    setView('menu');
  }

  function goBack() {
    if (busy) return;
    showStatus('');
    if (view === 'results') setView('search');
    else results();
  }
  back.addEventListener('click', goBack);
  tg?.BackButton?.onClick(goBack);
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    load(async () => {
      const data = await api('/search', {
        guests: Number(document.getElementById('guests').value),
        totalBudgetByn: Number(document.getElementById('budget').value), date: date.value,
        time: document.getElementById('time').value, cuisine: document.getElementById('cuisine').value || null
      });
      cards = data.cards; warnings = data.warnings;
      results();
    });
  });
  const naturalForm = document.getElementById('natural-form');
  naturalForm.addEventListener('submit', event => {
    event.preventDefault();
    if (!naturalForm.reportValidity()) return;
    const query = document.getElementById('query').value;
    if (!query.trim() || query.length > 2000) {
      showStatus('Напишите непустой запрос длиной до 2000 символов.');
      return;
    }
    load(async () => {
      const data = await api('/search/natural', { query });
      if (data.status === 'NEED_CLARIFICATION') return data.message;
      cards = data.cards; warnings = data.warnings;
      results();
    });
  });
})();
