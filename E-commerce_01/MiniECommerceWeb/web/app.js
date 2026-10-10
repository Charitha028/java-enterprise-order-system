(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const money = (n) => '₹' + Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 });

  const STATUS_LABEL = {
    PLACED: 'Placed',
    INVENTORY_RESERVED: 'Stock reserved',
    PAYMENT_SUCCESS: 'Paid',
    SHIPPED: 'Shipped',
    REJECTED_OUT_OF_STOCK: 'Out of stock',
    PAYMENT_FAILED: 'Payment failed'
  };

  // What each Java thread state means, in plain words
  const STATE_NOTE = {
    NEW: 'Starting up.',
    RUNNABLE: 'Running on the CPU.',
    WAITING: 'Idle. Sleeping in wait() until work arrives.',
    TIMED_WAITING: 'Working. Thread.sleep() stands in for processing time.',
    BLOCKED: 'Waiting for a synchronized lock.',
    TERMINATED: 'Stopped.'
  };

  const FILTERS = {
    all: () => true,
    progress: (o) => ['PLACED', 'INVENTORY_RESERVED', 'PAYMENT_SUCCESS'].includes(o.status),
    shipped: (o) => o.status === 'SHIPPED',
    problem: (o) => ['REJECTED_OUT_OF_STOCK', 'PAYMENT_FAILED'].includes(o.status)
  };

  const JOURNEY = [
    { by: 'a customer thread', title: 'Order placed', note: 'Added to the order queue.' },
    { by: 'Inventory-Thread', title: 'Stock reserved', note: 'Stock was checked and reserved inside a synchronized method.' },
    { by: 'Payment-Thread', title: 'Payment received', note: 'The payment gateway accepted the charge.' },
    { by: 'Shipping-Thread', title: 'Shipped', note: 'Packed and sent to the customer.' }
  ];
  const REACHED = { PLACED: 0, INVENTORY_RESERVED: 1, PAYMENT_SUCCESS: 2, SHIPPED: 3, REJECTED_OUT_OF_STOCK: 0, PAYMENT_FAILED: 1 };
  const FAIL_AT = { REJECTED_OUT_OF_STOCK: 1, PAYMENT_FAILED: 2 };

  let state = null;
  let filter = 'all';
  let paused = false;
  let openOrderId = null;
  let firstOrdersRender = true;
  let ordersSig = '';
  let logSig = '';
  let timer = null;
  let polling = false;
  const seenOrders = new Set();
  const maxStock = {};
  const picker = { built: '', radios: {} };
  const invRows = {};

  /* ---------- helpers ---------- */
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  function flash(node, message, kind) {
    node.textContent = message;
    node.className = 'feedback' + (kind ? ' ' + kind : '');
  }

  async function post(path, body) {
    const res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    let data = {};
    try { data = await res.json(); } catch (e) { /* empty body */ }
    if (!res.ok) throw new Error(data.error || 'The request failed. Try again.');
    return data;
  }

  /* ---------- polling ---------- */
  async function poll() {
    if (polling) return;
    polling = true;
    try {
      const res = await fetch('/api/state', { cache: 'no-store' });
      if (!res.ok) throw new Error('bad status');
      state = await res.json();
      render(state);
      setConn(true);
    } catch (e) {
      setConn(false);
    } finally {
      polling = false;
      timer = setTimeout(poll, 1000);
    }
  }

  function refreshNow() {
    clearTimeout(timer);
    poll();
  }

  function setConn(up) {
    const c = $('conn');
    c.className = 'conn ' + (up ? 'up' : 'down');
    c.textContent = up ? 'Connected to the Java server' : 'Server not reachable. Start Main.java and reload.';
  }

  /* ---------- rendering ---------- */
  function render(s) {
    renderPipeline(s);
    renderStats(s.stats);
    renderInventory(s.inventory);
    renderPicker(s.inventory);
    renderOrders(s.orders);
    renderLog(s.threads.logger, s.logs);
    if (openOrderId !== null) renderJourney(openOrderId);
  }

  function renderPipeline(s) {
    // customers pool
    const n = s.customersActive;
    $('st-customers').dataset.state = n > 0 ? 'ACTIVE' : 'IDLE';
    const bc = $('badge-customers');
    bc.dataset.state = n > 0 ? 'ACTIVE' : 'IDLE';
    bc.textContent = n > 0 ? 'Active' : 'Idle';
    $('doing-customers').textContent = n > 0
      ? n + (n === 1 ? ' customer is' : ' customers are') + ' placing orders'
      : 'No customers ordering';

    // worker threads
    ['inventory', 'payment', 'shipping'].forEach((key) => {
      const t = s.threads[key];
      $('st-' + key).dataset.state = t.state;
      $('who-' + key).textContent = t.name;
      const b = $('badge-' + key);
      b.dataset.state = t.state;
      b.textContent = t.state;
      $('doing-' + key).textContent = STATE_NOTE[t.state] || '';
    });

    setQueue('q-order', s.queues.order);
    setQueue('q-payment', s.queues.payment);
    setQueue('q-shipping', s.queues.shipping);
  }

  function setQueue(id, count) {
    const box = $(id);
    const slots = box.querySelector('.slots');
    if (!slots.children.length) {
      for (let i = 0; i < 12; i++) slots.appendChild(document.createElement('i'));
    }
    [...slots.children].forEach((dot, i) => dot.classList.toggle('on', i < count));
    box.classList.toggle('busy', count > 0);
    box.querySelector('.count b').textContent = count;
  }

  function renderStats(st) {
    $('s-total').textContent = st.total;
    $('s-progress').textContent = st.inProgress;
    $('s-shipped').textContent = st.shipped;
    $('s-failed').textContent = st.paymentFailed;
    $('s-rejected').textContent = st.rejected;
    $('s-revenue').textContent = money(st.revenue);
  }

  function renderInventory(items) {
    const list = $('inventory');
    items.forEach((p) => {
      maxStock[p.name] = Math.max(maxStock[p.name] || 0, p.stock);
      let row = invRows[p.name];
      if (!row) {
        const li = el('li');
        const name = el('div', 'name', p.name);
        const price = el('small', null, money(p.price));
        name.appendChild(price);
        const qty = el('div', 'qty', '');
        const bar = el('div', 'bar');
        const fill = el('i');
        bar.appendChild(fill);
        const btn = el('button', 'btn small restock', 'Restock +10');
        btn.type = 'button';
        btn.addEventListener('click', () => restock(p.name, btn));
        li.append(name, qty, bar, btn);
        list.appendChild(li);
        row = invRows[p.name] = { qty, fill };
      }
      row.qty.textContent = p.stock;
      const pct = Math.round((p.stock / maxStock[p.name]) * 100);
      row.fill.style.width = pct + '%';
      row.fill.className = p.stock === 0 ? 'out' : pct <= 25 ? 'low' : '';
    });
  }

  function renderPicker(items) {
    const key = items.map((p) => p.name).join('|');
    const box = $('picker');
    if (picker.built !== key) {
      box.textContent = '';
      picker.radios = {};
      items.forEach((p, i) => {
        const wrap = el('div', 'pick');
        const input = document.createElement('input');
        input.type = 'radio';
        input.name = 'product';
        input.id = 'p-' + i;
        input.value = p.name;
        if (i === 0) input.checked = true;
        const label = el('label');
        label.htmlFor = input.id;
        label.append(el('b', null, p.name), el('span', null, money(p.price)));
        const stock = el('em');
        label.appendChild(stock);
        wrap.append(input, label);
        box.appendChild(wrap);
        picker.radios[p.name] = stock;
      });
      picker.built = key;
    }
    items.forEach((p) => {
      const em = picker.radios[p.name];
      em.textContent = p.stock === 0 ? 'Out of stock' : p.stock + ' left';
      em.className = p.stock === 0 ? 'out' : p.stock <= 5 ? 'low' : '';
    });
  }

  function renderOrders(orders) {
    const rows = orders.filter(FILTERS[filter]);
    const sig = filter + '|' + rows.map((o) => o.id + o.status).join(',');
    $('orders-empty').hidden = rows.length > 0;
    $('orders-empty').textContent = orders.length === 0
      ? 'No orders yet. Place one on the left, or start a rush.'
      : 'No orders match this filter.';
    if (sig === ordersSig) return; // nothing changed; keep keyboard focus intact
    ordersSig = sig;

    const body = $('orders');
    body.textContent = '';
    rows.forEach((o) => {
      const tr = document.createElement('tr');
      if (!firstOrdersRender && !seenOrders.has(o.id)) tr.className = 'fresh';
      const idCell = el('td');
      const btn = el('button', 'link-btn', 'Order-' + o.id);
      btn.type = 'button';
      btn.dataset.id = o.id;
      btn.setAttribute('aria-label', 'Show journey of Order-' + o.id);
      idCell.appendChild(btn);
      const statusCell = el('td');
      statusCell.appendChild(el('span', 'status st-' + o.status, STATUS_LABEL[o.status] || o.status));
      tr.append(idCell, el('td', null, o.customer), el('td', null, o.product + ' × ' + o.quantity),
        el('td', 'num', money(o.total)), statusCell, el('td', null, o.time));
      body.appendChild(tr);
    });
    orders.forEach((o) => seenOrders.add(o.id));
    firstOrdersRender = false;
  }

  function renderLog(logger, logs) {
    const b = $('badge-logger');
    b.dataset.state = logger.state;
    b.textContent = logger.name + ': ' + logger.state;
    if (paused) return;
    const sig = logs.length + '|' + logs[0] + '|' + logs[logs.length - 1];
    if (sig === logSig) return;
    logSig = sig;
    const list = $('log');
    list.textContent = '';
    for (let i = logs.length - 1; i >= 0; i--) {
      const line = logs[i];
      const cut = line.indexOf(' ');
      const li = el('li', /FAILED|REJECTED/.test(line) ? 'bad' : /successful|shipped|reserved|Restocked/.test(line) ? 'good' : '');
      li.append(el('time', null, line.slice(0, cut)), el('span', null, line.slice(cut + 1)));
      list.appendChild(li);
    }
  }

  /* ---------- order journey dialog ---------- */
  function renderJourney(id) {
    const o = state && state.orders.find((x) => x.id === id);
    if (!o) return;
    $('j-title').textContent = 'Order-' + o.id;
    $('j-sub').textContent = o.customer + ' ordered ' + o.quantity + ' × ' + o.product + ' for ' + money(o.total) + '.';
    const reached = REACHED[o.status];
    const failAt = FAIL_AT[o.status];
    const list = $('j-steps');
    list.textContent = '';
    JOURNEY.forEach((step, i) => {
      let cls = '', mark = '', title = step.title, note = '';
      if (failAt === i) {
        cls = 'failed'; mark = '✕';
        title = i === 1 ? 'Out of stock' : 'Payment failed';
        note = i === 1
          ? 'Inventory-Thread found less stock than requested, so the order was rejected.'
          : 'Payment-Thread could not charge the customer, so the reserved stock was released.';
      } else if (failAt !== undefined && i > failAt) {
        cls = 'skipped'; mark = ''; note = 'Not reached.';
      } else if (i <= reached) {
        cls = 'done'; mark = '✓'; note = step.note + ' Handled by ' + step.by + '.';
      } else if (i === reached + 1) {
        cls = 'current'; mark = '…'; note = 'In progress. Waiting for ' + step.by + '.';
      } else {
        note = 'Waiting for the earlier steps.';
      }
      const li = el('li', cls);
      const text = el('div');
      text.append(el('b', null, title), el('small', null, note));
      li.append(el('div', 'mark', mark), text);
      list.appendChild(li);
    });
  }

  const dlg = $('journey');
  function openJourney(id) {
    openOrderId = id;
    renderJourney(id);
    if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open', '');
  }
  dlg.addEventListener('close', () => { openOrderId = null; });

  /* ---------- actions ---------- */
  async function restock(name, btn) {
    btn.disabled = true;
    try {
      await post('/api/restock', { product: name, quantity: 10 });
      flash($('inv-feedback'), name + ' restocked by 10.', 'ok');
      refreshNow();
    } catch (e) {
      flash($('inv-feedback'), e.message, 'err');
    } finally {
      btn.disabled = false;
    }
  }

  function clampQty(v) {
    const n = parseInt(v, 10);
    return Number.isNaN(n) ? 1 : Math.min(50, Math.max(1, n));
  }

  /* ---------- events ---------- */
  document.addEventListener('click', (e) => {
    const why = e.target.closest('.why');
    if (why) {
      const text = $(why.getAttribute('aria-controls'));
      const open = why.getAttribute('aria-expanded') === 'true';
      why.setAttribute('aria-expanded', String(!open));
      why.textContent = open ? 'How it works' : 'Hide';
      text.hidden = open;
    }
  });

  $('orders').addEventListener('click', (e) => {
    const btn = e.target.closest('.link-btn');
    if (btn) openJourney(Number(btn.dataset.id));
  });

  $('filters').addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    filter = chip.dataset.filter;
    document.querySelectorAll('.chip').forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
    ordersSig = '';
    if (state) renderOrders(state.orders);
  });

  $('btn-pause').addEventListener('click', (e) => {
    paused = !paused;
    e.currentTarget.setAttribute('aria-pressed', String(paused));
    e.currentTarget.textContent = paused ? 'Resume' : 'Pause';
    if (!paused && state) { logSig = ''; renderLog(state.threads.logger, state.logs); }
  });

  const qty = $('f-qty');
  $('qty-minus').addEventListener('click', () => { qty.value = clampQty(qty.value) - 1 || 1; });
  $('qty-plus').addEventListener('click', () => { qty.value = Math.min(50, clampQty(qty.value) + 1); });
  qty.addEventListener('change', () => { qty.value = clampQty(qty.value); });

  const nameInput = $('f-name');
  try { nameInput.value = localStorage.getItem('customerName') || ''; } catch (err) { /* storage blocked */ }
  nameInput.addEventListener('input', () => nameInput.classList.remove('invalid'));

  $('order-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fb = $('order-feedback');
    const name = nameInput.value.trim();
    if (!name) {
      nameInput.classList.add('invalid');
      nameInput.focus();
      flash(fb, 'Enter your name so we know who is ordering.', 'err');
      return;
    }
    const chosen = document.querySelector('input[name="product"]:checked');
    if (!chosen) { flash(fb, 'Choose a product.', 'err'); return; }

    const btn = $('btn-order');
    btn.disabled = true;
    try {
      const r = await post('/api/orders', { customerName: name, product: chosen.value, quantity: clampQty(qty.value) });
      try { localStorage.setItem('customerName', name); } catch (err) { /* ignore */ }
      flash(fb, 'Order-' + r.orderId + ' placed. Follow it through the pipeline above.', 'ok');
      refreshNow();
    } catch (err) {
      flash(fb, err.message, 'err');
    } finally {
      btn.disabled = false;
    }
  });

  const rc = $('r-customers');
  const re = $('r-each');
  function updateSim() {
    $('v-customers').textContent = rc.value;
    $('v-each').textContent = re.value;
    $('btn-sim').textContent = 'Start ' + (rc.value * re.value) + ' orders';
  }
  rc.addEventListener('input', updateSim);
  re.addEventListener('input', updateSim);
  updateSim();

  $('btn-sim').addEventListener('click', async (e) => {
    const btn = e.currentTarget;
    const fb = $('sim-feedback');
    btn.disabled = true;
    try {
      await post('/api/simulate', { customers: Number(rc.value), ordersPerCustomer: Number(re.value) });
      flash(fb, rc.value + ' customers are placing ' + (rc.value * re.value) + ' orders.', 'ok');
      refreshNow();
    } catch (err) {
      flash(fb, err.message, 'err');
    } finally {
      btn.disabled = false;
    }
  });

  poll();
})();
