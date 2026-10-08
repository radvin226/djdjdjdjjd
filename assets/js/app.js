// پوشاک مهربانو — فرانت‌اند (HTML/CSS/JS) برای GitHub Pages
// آدرس API را اینجا تنظیم کنید (بدون پایان‌بندی اضافه)
const API = 'https://api.mehrbanoopashak.ir/';
const STATUS = { pending: 'در انتظار', processing: 'در حال پردازش', shipped: 'ارسال شده', delivered: 'تحویل داده شده', cancelled: 'لغو شده' };

const $ = s => document.querySelector(s);
const money = n => Number(n).toLocaleString('fa-IR') + ' تومان';
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const get = (k, d = null) => JSON.parse(localStorage.getItem(k) || JSON.stringify(d));
const put = (k, v) => localStorage.setItem(k, JSON.stringify(v));
const cart = () => get('cart', []);
const thumb = p => p.image ? `<img src="${esc(p.image)}" alt="">` : esc(String(p.title).charAt(0));
const notice = (msg, isError = true) => $('#app').insertAdjacentHTML('afterbegin', `<div class="alert${isError ? ' error' : ''}">${esc(msg)}</div>`);

async function api(action, { method = 'GET', body, params = {} } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('token');
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(API + '?' + new URLSearchParams({ action, ...params }), {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'خطا در ارتباط با سرور');
  return data;
}

function renderHeader() {
  const user = get('user');
  const count = cart().reduce((s, x) => s + x.qty, 0);
  $('#topbar').innerHTML = `
    <a class="logo" href="index.html">پوشاک مهربانو</a>
    <nav>
      <a href="index.html">خانه</a>
      ${['زنانه', 'مردانه', 'بچگانه'].map(c => `<a href="index.html?cat=${encodeURIComponent(c)}">${c}</a>`).join('')}
      <a class="cart" href="cart.html">سبد خرید <span>${count}</span></a>
      ${user ? `<a href="account.html">حساب من</a>${user.is_admin ? '<a href="admin.html">پنل ادمین</a>' : ''}<a href="#" id="logout">خروج</a>`
        : '<a href="login.html">ورود</a><a class="btn btn-sm" href="login.html?mode=register">ثبت نام</a>'}
    </nav>`;
  $('#logout')?.addEventListener('click', e => {
    e.preventDefault();
    api('logout', { method: 'POST' }).catch(() => {}).finally(() => {
      localStorage.removeItem('token'); localStorage.removeItem('user'); location.href = 'index.html';
    });
  });
}

async function renderBanner() {
  const bf = await api('bf');
  if (!bf.percent) return;
  $('#banner').innerHTML = `<div class="bf-banner"><span>بلک فرایدی: تخفیف ${bf.percent}٪ روی همه محصولات</span>${bf.ends ? '<span id="countdown"></span>' : ''}</div>`;
  if (!bf.ends) return;
  const pad = n => String(n).padStart(2, '0');
  const tick = () => {
    const s = Math.max(0, Math.floor((bf.ends - Date.now()) / 1000));
    $('#countdown').textContent = `${Math.floor(s / 86400)} روز ${pad(Math.floor(s % 86400 / 3600))}:${pad(Math.floor(s % 3600 / 60))}:${pad(s % 60)}`;
  };
  tick(); setInterval(tick, 1000);
}

const card = p => `
  <a class="card" href="product.html?id=${p.id}">
    <div class="thumb">${thumb(p)}</div>
    <div class="info">
      <h3>${esc(p.title)}</h3>
      ${p.price.percent ? `<span class="badge">٪${p.price.percent}</span> <del>${money(p.price.price)}</del><br>` : ''}
      <span class="price">${money(p.price.final)}</span>
      ${p.stock < 1 ? '<div class="out">ناموجود</div>' : ''}
    </div>
  </a>`;

async function home() {
  const q = new URLSearchParams(location.search);
  const cat = q.get('cat') || '', term = q.get('q') || '';
  $('#app').innerHTML = `
    <section class="hero">
      <h1>لباسی برای هر روز، با مهربانی</h1>
      <p>مجموعه زنانه، مردانه و بچگانه؛ با سایز و رنگ دلخواه خودتان.</p>
      <a class="btn btn-sm" href="#shop">دیدن محصولات</a>
    </section>
    <form class="search" id="shop">
      <input name="q" value="${esc(term)}" placeholder="جستجوی محصول">
      <select name="cat"><option value="">همه دسته‌ها</option>
        ${['زنانه', 'مردانه', 'بچگانه'].map(c => `<option${c === cat ? ' selected' : ''}>${c}</option>`).join('')}
      </select>
      <button class="btn">جستجو</button>
    </form>
    <div class="grid" id="grid"></div>`;
  $('#shop').onsubmit = e => {
    e.preventDefault();
    const f = e.target.elements;
    location.search = new URLSearchParams({ cat: f.cat.value, q: f.q.value }).toString();
  };
  const list = await api('products', { params: { cat, q: term } });
  $('#grid').innerHTML = list.map(card).join('') || '<p>محصولی پیدا نشد.</p>';
}

async function product() {
  const p = await api('product', { params: { id: new URLSearchParams(location.search).get('id') } });
  const pr = p.price;
  $('#app').innerHTML = `
    <div class="product">
      <div class="thumb big">${thumb(p)}</div>
      <div>
        <p class="note">${esc(p.category)}</p>
        <h1>${esc(p.title)}</h1>
        ${pr.percent ? `<p><del>${money(pr.price)}</del> <span class="badge">٪${pr.percent} تخفیف</span></p>` : ''}
        <p class="price">${money(pr.final)}</p>
        <p>${esc(p.description).replace(/\n/g, '<br>')}</p>
        ${p.stock > 0 ? `
        <form class="form" id="buy">
          <label>سایز <select name="size">${p.sizes.map(s => `<option>${esc(s)}</option>`).join('')}</select></label>
          <label>رنگ / مدل <select name="color">${p.colors.map(c => `<option>${esc(c)}</option>`).join('')}</select></label>
          <label>تعداد <input type="number" name="qty" value="1" min="1" max="10"></label>
          <button class="btn">افزودن به سبد خرید</button>
        </form>` : '<p class="out">ناموجود</p>'}
      </div>
    </div>`;
  $('#buy')?.addEventListener('submit', e => {
    e.preventDefault();
    const f = e.target.elements;
    const items = cart();
    const item = { id: p.id, size: f.size.value, color: f.color.value, qty: Math.min(10, Math.max(1, +f.qty.value || 1)) };
    const hit = items.find(x => x.id === item.id && x.size === item.size && x.color === item.color);
    if (hit) hit.qty = Math.min(10, hit.qty + item.qty); else items.push(item);
    put('cart', items);
    location.href = 'cart.html';
  });
}

async function summary() {
  const byId = Object.fromEntries((await api('products')).map(p => [p.id, p]));
  const lines = cart().map((it, idx) => ({ ...it, idx, p: byId[it.id] })).filter(l => l.p);
  const subtotal = lines.reduce((s, l) => s + l.p.price.final * l.qty, 0);
  const coupon = get('coupon');
  const discount = coupon ? Math.round(subtotal * coupon.percent / 100) : 0;
  return { lines, subtotal, coupon, discount, total: subtotal - discount };
}

async function cartPage() {
  if (!cart().length) { $('#app').innerHTML = '<p>سبد خرید شما خالی است. <a href="index.html">ادامه خرید</a></p>'; return; }
  const s = await summary();
  $('#app').innerHTML = `
    <h1>سبد خرید</h1>
    <table class="cart">
      <tr><th>محصول</th><th>سایز / رنگ</th><th>قیمت واحد</th><th>تعداد</th><th>جمع</th><th></th></tr>
      ${s.lines.map(l => `
      <tr>
        <td>${esc(l.p.title)}${l.p.price.percent ? ` <span class="badge">٪${l.p.price.percent}</span>` : ''}</td>
        <td>${esc(l.size)} / ${esc(l.color)}</td>
        <td>${money(l.p.price.final)}</td>
        <td><input type="number" min="0" max="10" value="${l.qty}" data-i="${l.idx}" style="width:90px"></td>
        <td>${money(l.p.price.final * l.qty)}</td>
        <td><button class="link danger" data-rm="${l.idx}">حذف</button></td>
      </tr>`).join('')}
    </table>
    <form class="inline" id="coupon" style="margin-top:18px">
      <input name="code" placeholder="کد تخفیف" value="${esc(s.coupon?.code || '')}" style="width:220px">
      <button class="btn btn-sm">اعمال کد</button>
    </form>
    <div class="totals">
      <p>جمع کل: ${money(s.subtotal)}</p>
      ${s.discount ? `<p>تخفیف کد (٪${s.coupon.percent}): ${money(s.discount)} -</p>` : ''}
      <p class="total">قابل پرداخت: ${money(s.total)}</p>
      <a class="btn" href="checkout.html">ثبت سفارش</a>
    </div>`;
  document.querySelectorAll('[data-i]').forEach(inp => inp.onchange = () => {
    const items = cart(), i = +inp.dataset.i;
    if (+inp.value < 1) items.splice(i, 1); else items[i].qty = Math.min(10, +inp.value);
    put('cart', items); location.reload();
  });
  document.querySelectorAll('[data-rm]').forEach(b => b.onclick = () => {
    const items = cart(); items.splice(+b.dataset.rm, 1); put('cart', items); location.reload();
  });
  $('#coupon').onsubmit = async e => {
    e.preventDefault();
    const code = e.target.elements.code.value.trim();
    localStorage.removeItem('coupon');
    if (code) {
      try { put('coupon', await api('coupon', { method: 'POST', body: { code } })); }
      catch (err) { alert(err.message); }
    }
    location.reload();
  };
}

async function checkout() {
  if (!cart().length) return location.href = 'cart.html';
  if (!localStorage.getItem('token')) return location.href = 'login.html?next=checkout.html';
  const s = await summary();
  const u = get('user', {});
  $('#app').innerHTML = `
    <h1>ثبت سفارش</h1>
    <div class="checkout">
      <form class="form box" id="order">
        <label>نام و نام خانوادگی <input name="full_name" required value="${esc(u.name)}"></label>
        <label>شماره موبایل <input name="phone" required placeholder="09123456789"></label>
        <label>آدرس کامل <textarea name="address" rows="3" required></textarea></label>
        <p class="note">پرداخت آنلاین فعال نیست؛ پرداخت هنگام تحویل یا از طریق هماهنگی با فروشگاه انجام می‌شود.</p>
        <button class="btn">ثبت سفارش</button>
      </form>
      <aside class="totals">
        <p>جمع کل: ${money(s.subtotal)}</p>
        ${s.discount ? `<p>تخفیف کد: ${money(s.discount)} -</p>` : ''}
        <p class="total">قابل پرداخت: ${money(s.total)}</p>
      </aside>
    </div>`;
  $('#order').onsubmit = async e => {
    e.preventDefault();
    const f = e.target.elements;
    try {
      const r = await api('checkout', { method: 'POST', body: {
        items: cart(), coupon: s.coupon?.code || '',
        full_name: f.full_name.value, phone: f.phone.value, address: f.address.value } });
      localStorage.removeItem('cart'); localStorage.removeItem('coupon');
      localStorage.setItem('flash', `سفارش #${r.order_id} ثبت شد. به زودی با شما تماس می‌گیریم.`);
      location.href = 'account.html';
    } catch (err) { notice(err.message); }
  };
}

function login() {
  const q = new URLSearchParams(location.search);
  const reg = q.get('mode') === 'register';
  const next = ['checkout.html', 'cart.html', 'account.html'].includes(q.get('next')) ? q.get('next') : 'index.html';
  $('#app').innerHTML = `
    <div class="auth box">
      <h1>${reg ? 'ساخت حساب' : 'ورود به حساب'}</h1>
      <form class="form" id="auth">
        ${reg ? '<label>نام و نام خانوادگی <input name="name" required></label><label>شماره موبایل <input name="phone"></label>' : ''}
        <label>ایمیل <input type="email" name="email" required></label>
        <label>رمز عبور <input type="password" name="password" required></label>
        <button class="btn">${reg ? 'ثبت نام' : 'ورود'}</button>
      </form>
      <p class="note"><a href="login.html${reg ? '' : '?mode=register'}">${reg ? 'حساب دارید؟ وارد شوید' : 'حساب ندارید؟ ثبت نام کنید'}</a></p>
    </div>`;
  $('#auth').onsubmit = async e => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    try {
      const r = await api(reg ? 'register' : 'login', { method: 'POST', body });
      localStorage.setItem('token', r.token);
      put('user', r.user);
      location.href = r.user.is_admin ? 'admin.html' : next;
    } catch (err) { notice(err.message); }
  };
}

async function account() {
  const u = get('user');
  if (!u) return location.href = 'login.html';
  const orders = await api('orders');
  $('#app').innerHTML = `<h1>سلام، ${esc(u.name)}</h1>` + (orders.length
    ? `<table class="cart"><tr><th>شماره</th><th>تاریخ</th><th>مبلغ</th><th>وضعیت</th></tr>
       ${orders.map(o => `<tr><td>#${o.id}</td><td>${esc(o.created_at)}</td><td>${money(o.total)}</td><td>${STATUS[o.status]}</td></tr>`).join('')}</table>`
    : '<p>هنوز سفارشی ثبت نکرده‌اید. <a href="index.html">رفتن به فروشگاه</a></p>');
  const msg = localStorage.getItem('flash');
  if (msg) { localStorage.removeItem('flash'); notice(msg, false); }
}

async function admin() {
  const u = get('user');
  if (!u || !u.is_admin) { $('#app').innerHTML = '<p>دسترسی ندارید. <a href="login.html">ورود</a></p>'; return; }
  const d = await api('admin_data');
  $('#app').innerHTML = `
    <h1>پنل مدیریت</h1>
    <section class="stats">
      <div>تعداد سفارش‌ها: <b>${d.orders.length}</b></div>
      <div>بلک فرایدی: <b>${d.bf.active ? 'فعال' : 'غیرفعال'}</b></div>
    </section>

    <section class="box">
      <h2>بلک فرایدی</h2>
      <form class="form inline" id="bf">
        <label><input type="checkbox" name="active" ${d.bf.active ? 'checked' : ''} style="width:auto"> فعال باشد</label>
        <label>درصد تخفیف <input type="number" name="percent" min="0" max="90" value="${d.bf.percent}"></label>
        <label>پایان کمپین <input type="datetime-local" name="ends" value="${esc(String(d.bf.ends).replace(' ', 'T'))}"></label>
        <button class="btn">ذخیره</button>
      </form>
    </section>

    <section class="box">
      <h2>محصولات</h2>
      <form class="form grid2" id="prod">
        <input type="hidden" name="id" value="0">
        <label>عنوان <input name="title" required></label>
        <label>دسته <select name="category"><option>زنانه</option><option>مردانه</option><option>بچگانه</option></select></label>
        <label>قیمت (تومان) <input type="number" name="price" required></label>
        <label>تخفیف اختصاصی (٪) <input type="number" name="discount_percent" min="0" max="90" value="0"></label>
        <label>سایزها (با ویرگول) <input name="sizes" value="S,M,L,XL"></label>
        <label>رنگ / مدل‌ها (با ویرگول) <input name="colors" value="مشکی,سفید"></label>
        <label>موجودی <input type="number" name="stock" value="10"></label>
        <label>آدرس تصویر (اختیاری) <input name="image"></label>
        <label class="full">توضیحات <textarea name="description" rows="2"></textarea></label>
        <label><input type="checkbox" name="is_active" checked style="width:auto"> نمایش در سایت</label>
        <button class="btn">ذخیره محصول</button>
      </form>
      <table class="cart" style="margin-top:20px">
        <tr><th>عنوان</th><th>دسته</th><th>قیمت</th><th>تخفیف</th><th>موجودی</th><th></th></tr>
        ${d.products.map((p, i) => `<tr>
          <td>${esc(p.title)}${p.is_active ? '' : ' (مخفی)'}</td><td>${esc(p.category)}</td>
          <td>${money(p.price.price)}</td><td>${p.price.percent}٪</td><td>${p.stock}</td>
          <td><button class="link" data-edit="${i}">ویرایش</button> <button class="link danger" data-del="${p.id}">حذف</button></td>
        </tr>`).join('')}
      </table>
    </section>

    <section class="box">
      <h2>سفارش‌ها</h2>
      <table class="cart">
        <tr><th>شماره</th><th>مشتری</th><th>مبلغ</th><th>وضعیت</th></tr>
        ${d.orders.map(o => `<tr>
          <td>#${o.id}</td>
          <td>${esc(o.full_name)}<br><small>${esc(o.phone)} — ${esc(o.address)}</small></td>
          <td>${money(o.total)}${o.coupon ? ' (' + esc(o.coupon) + ')' : ''}</td>
          <td><select data-order="${o.id}">${Object.entries(STATUS).map(([k, v]) => `<option value="${k}"${o.status === k ? ' selected' : ''}>${v}</option>`).join('')}</select></td>
        </tr>`).join('')}
      </table>
    </section>

    <section class="box">
      <h2>کدهای تخفیف</h2>
      <form class="form inline" id="cp">
        <input name="code" placeholder="مثلا BF30" required>
        <input type="number" name="percent" min="1" max="90" placeholder="٪" required>
        <button class="btn">افزودن</button>
      </form>
      <ul class="coupons">${d.coupons.map(c => `<li><b>${esc(c.code)}</b> — ${c.percent}٪ <button class="link danger" data-coupon="${c.id}">حذف</button></li>`).join('')}</ul>
    </section>`;

  const reload = () => location.reload();
  const fail = err => notice(err.message);
  $('#bf').onsubmit = e => {
    e.preventDefault();
    const f = e.target.elements;
    api('admin_settings', { method: 'POST', body: { active: f.active.checked, percent: f.percent.value, ends: f.ends.value } }).then(reload, fail);
  };
  $('#prod').onsubmit = e => {
    e.preventDefault();
    const f = e.target;
    const body = Object.fromEntries(new FormData(f));
    body.is_active = f.elements.is_active.checked;
    api('admin_product', { method: 'POST', body }).then(reload, fail);
  };
  $('#cp').onsubmit = e => {
    e.preventDefault();
    const f = e.target.elements;
    api('admin_coupon', { method: 'POST', body: { code: f.code.value, percent: f.percent.value } }).then(reload, fail);
  };
  document.querySelectorAll('[data-coupon]').forEach(b => b.onclick = () =>
    api('admin_coupon', { method: 'POST', body: { id: b.dataset.coupon, delete: true } }).then(reload, fail));
  document.querySelectorAll('[data-del]').forEach(b => b.onclick = () => {
    if (confirm('این محصول حذف شود؟')) api('admin_product', { method: 'POST', body: { id: b.dataset.del, delete: true } }).then(reload, fail);
  });
  document.querySelectorAll('[data-order]').forEach(s => s.onchange = () =>
    api('admin_order', { method: 'POST', body: { id: s.dataset.order, status: s.value } }).then(reload, fail));
  document.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => {
    const p = d.products[+b.dataset.edit], f = $('#prod').elements;
    f.id.value = p.id; f.title.value = p.title; f.category.value = p.category; f.price.value = p.price.price;
    f.discount_percent.value = p.price.percent; f.sizes.value = p.sizes.join(','); f.colors.value = p.colors.join(',');
    f.stock.value = p.stock; f.image.value = p.image || ''; f.description.value = p.description || ''; f.is_active.checked = !!p.is_active;
    scrollTo(0, 0);
  });
}

const pages = { home, product, cart: cartPage, checkout, login, account, admin };
renderHeader();
renderBanner().catch(() => {});
(pages[document.body.dataset.page] || (() => {}))().catch(err => notice(err.message));
