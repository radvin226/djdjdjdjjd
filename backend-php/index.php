<?php
// پوشاک مهربانو — API (PHP). این فایل را در ریشه زیردامنه api.mehrbanoopashak.ir قرار دهید.
ini_set('display_errors', '0');
const DB_HOST = 'localhost';
const DB_NAME = 'pooshak_mehraboo';
const DB_USER = 'root';
const DB_PASS = '';
const FRONT_ORIGIN = 'https://mehrbanoopashak.ir'; // اگر با www هم باز می‌شود، باید جدا تنظیم شود

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: ' . FRONT_ORIGIN);
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') exit;

function db(): PDO {
    static $pdo;
    return $pdo ??= new PDO('mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4', DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
}
function out($data, int $code = 200): void { http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE); exit; }
function fail(string $msg, int $code = 400): void { out(['error' => $msg], $code); }
function input(): array { return json_decode(file_get_contents('php://input'), true) ?: []; }

function bearer(): string {
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    return preg_match('/^Bearer ([a-f0-9]{64})$/', $h, $m) ? $m[1] : '';
}
function user(): ?array {
    static $u = false;
    if ($u === false) {
        $u = null;
        $t = bearer();
        if ($t !== '') {
            $s = db()->prepare('SELECT u.id, u.name, u.email, u.is_admin FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?');
            $s->execute([$t]);
            $u = $s->fetch() ?: null;
        }
    }
    return $u;
}
function need_user(): array { $u = user(); if (!$u) fail('ابتدا وارد شوید', 401); return $u; }
function need_admin(): array { $u = need_user(); if (!$u['is_admin']) fail('دسترسی ندارید', 403); return $u; }
function start_session(int $uid): array {
    $t = bin2hex(random_bytes(32));
    db()->prepare('INSERT INTO sessions (token, user_id) VALUES (?, ?)')->execute([$t, $uid]);
    $s = db()->prepare('SELECT id, name, email, is_admin FROM users WHERE id = ?');
    $s->execute([$uid]);
    $u = $s->fetch();
    $u['id'] = (int)$u['id'];
    $u['is_admin'] = (int)$u['is_admin'];
    return ['token' => $t, 'user' => $u];
}

function setting(string $k): ?string {
    $s = db()->prepare('SELECT v FROM settings WHERE k = ?');
    $s->execute([$k]);
    $v = $s->fetchColumn();
    return $v === false ? null : $v;
}
// بلک فرایدی: درصد سراسری تا تاریخ پایان
function bf(): array {
    $ends = setting('bf_ends');
    $on = setting('bf_active') === '1' && (!$ends || strtotime($ends) > time());
    return ['percent' => $on ? (int)setting('bf_percent') : 0, 'ends' => $ends ? strtotime($ends) * 1000 : 0];
}
// قیمت نهایی = بیشترین بین تخفیف اختصاصی محصول و تخفیف بلک فرایدی
function price_of(array $p, int $bf): array {
    $pct = max((int)$p['discount_percent'], $bf);
    return ['price' => (int)$p['price'], 'percent' => $pct, 'final' => (int)round($p['price'] * (100 - $pct) / 100)];
}
function product_view(array $p, int $bf): array {
    return [
        'id' => (int)$p['id'], 'title' => $p['title'], 'category' => $p['category'], 'description' => $p['description'],
        'image' => $p['image'], 'stock' => (int)$p['stock'], 'is_active' => (int)$p['is_active'],
        'sizes' => array_map('trim', explode(',', (string)$p['sizes'])),
        'colors' => array_map('trim', explode(',', (string)$p['colors'])),
        'price' => price_of($p, $bf),
    ];
}

switch ($_GET['action'] ?? '') {
    case 'bf':
        out(bf());

    case 'products':
        $sql = 'SELECT * FROM products WHERE is_active = 1';
        $args = [];
        if (!empty($_GET['cat'])) { $sql .= ' AND category = ?'; $args[] = $_GET['cat']; }
        if (!empty($_GET['q'])) { $sql .= ' AND title LIKE ?'; $args[] = '%' . $_GET['q'] . '%'; }
        $s = db()->prepare($sql . ' ORDER BY id DESC');
        $s->execute($args);
        $bf = bf()['percent'];
        out(array_map(fn($p) => product_view($p, $bf), $s->fetchAll()));

    case 'product':
        $s = db()->prepare('SELECT * FROM products WHERE id = ? AND is_active = 1');
        $s->execute([(int)($_GET['id'] ?? 0)]);
        $p = $s->fetch();
        if (!$p) fail('محصول پیدا نشد', 404);
        out(product_view($p, bf()['percent']));

    case 'register':
        $in = input();
        $name = trim($in['name'] ?? '');
        $email = strtolower(trim($in['email'] ?? ''));
        $pass = (string)($in['password'] ?? '');
        if (mb_strlen($name) < 3) fail('نام را وارد کنید');
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('ایمیل معتبر نیست');
        if (strlen($pass) < 6) fail('رمز عبور باید حداقل ۶ کاراکتر باشد');
        $ex = db()->prepare('SELECT 1 FROM users WHERE email = ?');
        $ex->execute([$email]);
        if ($ex->fetch()) fail('این ایمیل قبلا ثبت شده است');
        db()->prepare('INSERT INTO users (name, email, phone, password) VALUES (?,?,?,?)')
            ->execute([$name, $email, trim($in['phone'] ?? ''), password_hash($pass, PASSWORD_DEFAULT)]);
        out(start_session((int)db()->lastInsertId()));

    case 'login':
        $in = input();
        $s = db()->prepare('SELECT * FROM users WHERE email = ?');
        $s->execute([strtolower(trim($in['email'] ?? ''))]);
        $u = $s->fetch();
        if (!$u || !password_verify((string)($in['password'] ?? ''), $u['password'])) fail('ایمیل یا رمز عبور اشتباه است', 401);
        out(start_session((int)$u['id']));

    case 'logout':
        if (bearer() !== '') db()->prepare('DELETE FROM sessions WHERE token = ?')->execute([bearer()]);
        out(['ok' => true]);

    case 'coupon':
        $s = db()->prepare('SELECT code, percent FROM coupons WHERE code = ? AND is_active = 1');
        $s->execute([strtoupper(trim(input()['code'] ?? ''))]);
        $c = $s->fetch();
        if (!$c) fail('کد تخفیف معتبر نیست');
        out($c);

    case 'orders':
        $u = need_user();
        $s = db()->prepare('SELECT id, created_at, total, status FROM orders WHERE user_id = ? ORDER BY id DESC');
        $s->execute([$u['id']]);
        out($s->fetchAll());

    case 'checkout':
        $u = need_user();
        $in = input();
        $items = $in['items'] ?? [];
        $name = trim($in['full_name'] ?? '');
        $phone = trim($in['phone'] ?? '');
        $addr = trim($in['address'] ?? '');
        if (!$items) fail('سبد خرید خالی است');
        if (mb_strlen($name) < 3) fail('نام را وارد کنید');
        if (!preg_match('/^09\d{9}$/', $phone)) fail('شماره موبایل معتبر نیست');
        if (mb_strlen($addr) < 10) fail('آدرس را کامل وارد کنید');

        $bf = bf()['percent'];
        $pdo = db();
        $pdo->beginTransaction();
        try {
            $ps = $pdo->prepare('SELECT * FROM products WHERE id = ? AND is_active = 1 FOR UPDATE');
            $lines = [];
            $subtotal = 0;
            foreach ($items as $it) {
                $ps->execute([(int)($it['id'] ?? 0)]);
                $p = $ps->fetch();
                if (!$p) throw new RuntimeException('محصولی در سبد معتبر نیست');
                $view = product_view($p, $bf);
                $qty = max(1, min(10, (int)($it['qty'] ?? 1)));
                if (!in_array($it['size'] ?? '', $view['sizes'], true) || !in_array($it['color'] ?? '', $view['colors'], true)) {
                    throw new RuntimeException('سایز یا رنگ «' . $p['title'] . '» معتبر نیست');
                }
                if ((int)$p['stock'] < $qty) throw new RuntimeException('موجودی «' . $p['title'] . '» کافی نیست');
                $unit = $view['price']['final'];
                $subtotal += $unit * $qty;
                $lines[] = [$p, $it['size'], $it['color'], $qty, $unit];
            }

            $pct = 0; $code = null;
            if (!empty($in['coupon'])) {
                $cs = $pdo->prepare('SELECT code, percent FROM coupons WHERE code = ? AND is_active = 1');
                $cs->execute([strtoupper(trim($in['coupon']))]);
                if ($c = $cs->fetch()) { $pct = (int)$c['percent']; $code = $c['code']; }
            }
            $discount = (int)round($subtotal * $pct / 100);
            $total = $subtotal - $discount;

            $pdo->prepare('INSERT INTO orders (user_id, full_name, phone, address, subtotal, discount, total, coupon) VALUES (?,?,?,?,?,?,?,?)')
                ->execute([$u['id'], $name, $phone, $addr, $subtotal, $discount, $total, $code]);
            $oid = (int)$pdo->lastInsertId();
            $ins = $pdo->prepare('INSERT INTO order_items (order_id, product_id, title, size, color, unit_price, qty) VALUES (?,?,?,?,?,?,?)');
            $dec = $pdo->prepare('UPDATE products SET stock = stock - ? WHERE id = ?');
            foreach ($lines as [$p, $size, $color, $qty, $unit]) {
                $ins->execute([$oid, $p['id'], $p['title'], $size, $color, $unit, $qty]);
                $dec->execute([$qty, $p['id']]);
            }
            $pdo->commit();
            out(['order_id' => $oid, 'total' => $total]);
        } catch (Throwable $ex) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            fail($ex instanceof RuntimeException ? $ex->getMessage() : 'خطا در ثبت سفارش', 409);
        }

    case 'admin_data':
        need_admin();
        $pdo = db();
        out([
            'products' => array_map(fn($p) => product_view($p, 0), $pdo->query('SELECT * FROM products ORDER BY id DESC')->fetchAll()),
            'orders' => $pdo->query('SELECT id, full_name, phone, address, total, coupon, status, created_at FROM orders ORDER BY id DESC LIMIT 100')->fetchAll(),
            'coupons' => $pdo->query('SELECT * FROM coupons ORDER BY id DESC')->fetchAll(),
            'bf' => [
                'active' => setting('bf_active') === '1',
                'percent' => (int)setting('bf_percent'),
                'ends' => substr((string)setting('bf_ends'), 0, 16),
            ],
        ]);

    case 'admin_product':
        need_admin();
        $in = input();
        $id = (int)($in['id'] ?? 0);
        $pdo = db();
        if (!empty($in['delete'])) {
            $pdo->prepare('DELETE FROM products WHERE id = ?')->execute([$id]);
            out(['ok' => true]);
        }
        $vals = [
            trim($in['title'] ?? ''), $in['category'] ?? 'زنانه', trim($in['description'] ?? ''),
            (int)($in['price'] ?? 0), min(90, max(0, (int)($in['discount_percent'] ?? 0))),
            trim($in['sizes'] ?? ''), trim($in['colors'] ?? ''), max(0, (int)($in['stock'] ?? 0)),
            trim($in['image'] ?? '') ?: null, empty($in['is_active']) ? 0 : 1,
        ];
        if ($vals[0] === '' || $vals[3] <= 0) fail('عنوان و قیمت را وارد کنید');
        if ($id) {
            $pdo->prepare('UPDATE products SET title=?, category=?, description=?, price=?, discount_percent=?, sizes=?, colors=?, stock=?, image=?, is_active=? WHERE id=?')
                ->execute([...$vals, $id]);
        } else {
            $pdo->prepare('INSERT INTO products (title, category, description, price, discount_percent, sizes, colors, stock, image, is_active) VALUES (?,?,?,?,?,?,?,?,?,?)')
                ->execute($vals);
        }
        out(['ok' => true]);

    case 'admin_order':
        need_admin();
        $in = input();
        if (!in_array($in['status'] ?? '', ['pending', 'processing', 'shipped', 'delivered', 'cancelled'], true)) fail('وضعیت نامعتبر است');
        db()->prepare('UPDATE orders SET status = ? WHERE id = ?')->execute([$in['status'], (int)($in['id'] ?? 0)]);
        out(['ok' => true]);

    case 'admin_settings':
        need_admin();
        $in = input();
        $set = db()->prepare('REPLACE INTO settings (k, v) VALUES (?, ?)');
        $set->execute(['bf_active', empty($in['active']) ? '0' : '1']);
        $set->execute(['bf_percent', (string)min(90, max(0, (int)($in['percent'] ?? 0)))]);
        $set->execute(['bf_ends', ($in['ends'] ?? '') ?: null]);
        out(['ok' => true]);

    case 'admin_coupon':
        need_admin();
        $in = input();
        if (!empty($in['delete'])) {
            db()->prepare('DELETE FROM coupons WHERE id = ?')->execute([(int)($in['id'] ?? 0)]);
        } else {
            $code = strtoupper(trim($in['code'] ?? ''));
            if ($code === '') fail('کد را وارد کنید');
            db()->prepare('INSERT IGNORE INTO coupons (code, percent) VALUES (?, ?)')
                ->execute([$code, min(90, max(1, (int)($in['percent'] ?? 0)))]);
        }
        out(['ok' => true]);

    default:
        fail('درخواست نامعتبر', 404);
}
