package app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

// پوشاک مهربانو — API جاوا (همان قرارداد API نسخه PHP)
public class Main {
    static final String DB_URL = env("DB_URL", "jdbc:mysql://localhost:3306/pooshak_mehraboo?useUnicode=true&characterEncoding=utf8&tinyInt1isBit=false&serverTimezone=UTC");
    static final String DB_USER = env("DB_USER", "root");
    static final String DB_PASS = env("DB_PASS", "");
    static final String FRONT_ORIGIN = env("FRONT_ORIGIN", "https://mehrbanoopashak.ir");
    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final SecureRandom RNG = new SecureRandom();

    record Ctx(HttpExchange ex, Map<String, String> params, String raw, Connection c) {
        String q(String k) { return params.getOrDefault(k, ""); }

        @SuppressWarnings("unchecked")
        Map<String, Object> in() { return raw.isBlank() ? new HashMap<>() : GSON.fromJson(raw, Map.class); }
    }

    record Line(Map<String, Object> p, String size, String color, int qty, int unit) {}

    static class ApiError extends RuntimeException {
        final int status;
        ApiError(int status, String msg) { super(msg); this.status = status; }
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        // فقط از داخل سرور قابل دسترسی است؛ از طریق Nginx به دامنه وصل می‌شود
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("API running on 127.0.0.1:" + port);
    }

    static void handle(HttpExchange ex) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", FRONT_ORIGIN);
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Content-Type", "application/json; charset=utf-8");
        try {
            if ("OPTIONS".equals(ex.getRequestMethod())) { send(ex, 204, null); return; }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                Object res = route(new Ctx(ex, parseQuery(ex.getRequestURI().getRawQuery()), body, c));
                send(ex, 200, res);
            }
        } catch (ApiError e) {
            send(ex, e.status, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, Map.of("error", "خطای داخلی سرور"));
        }
    }

    static Object route(Ctx x) throws SQLException {
        return switch (x.q("action")) {
            case "bf" -> bf(x.c());
            case "products" -> productsRoute(x);
            case "product" -> productRoute(x);
            case "register" -> registerRoute(x);
            case "login" -> loginRoute(x);
            case "logout" -> logoutRoute(x);
            case "coupon" -> couponRoute(x);
            case "orders" -> ordersRoute(x);
            case "checkout" -> checkoutRoute(x);
            case "admin_data" -> adminDataRoute(x);
            case "admin_product" -> adminProductRoute(x);
            case "admin_order" -> adminOrderRoute(x);
            case "admin_settings" -> adminSettingsRoute(x);
            case "admin_coupon" -> adminCouponRoute(x);
            default -> throw new ApiError(404, "درخواست نامعتبر");
        };
    }

    // ---------- helpers ----------
    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(o.toString().trim()); } catch (NumberFormatException e) { return 0; }
    }
    static String str(Object o) { return o == null ? "" : o.toString().trim(); }
    static String raw(Object o) { return o == null ? "" : o.toString(); }
    static boolean flag(Object o) {
        if (o == null) return false;
        String s = o.toString();
        return !s.isEmpty() && !s.equals("0") && !s.equals("false");
    }
    static List<String> csv(Object o) {
        List<String> out = new ArrayList<>();
        for (String s : str(o).split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] p = kv.split("=", 2);
            m.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                  p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
    static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        ex.close();
    }

    // ---------- database ----------
    static List<Map<String, Object>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof Timestamp t) v = t.toLocalDateTime().toString().replace('T', ' ');
                        row.put(md.getColumnLabel(i), v);
                    }
                    out.add(row);
                }
                return out;
            }
        }
    }
    static Map<String, Object> one(Connection c, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = query(c, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }
    static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getLong(1); }
        }
    }
    static String setting(Connection c, String k) throws SQLException {
        Map<String, Object> r = one(c, "SELECT v FROM settings WHERE k = ?", k);
        return r == null || r.get("v") == null ? null : r.get("v").toString();
    }

    // ---------- pricing / shop logic ----------
    static long parseDate(String s) {
        try { return LocalDateTime.parse(s.trim().replace(' ', 'T')).atZone(TEHRAN).toInstant().toEpochMilli(); }
        catch (Exception e) { return 0; }
    }
    // بلک فرایدی: درصد سراسری تا تاریخ پایان
    static Map<String, Object> bf(Connection c) throws SQLException {
        long ends = parseDate(raw(setting(c, "bf_ends")));
        boolean on = "1".equals(setting(c, "bf_active")) && (ends == 0 || ends > System.currentTimeMillis());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("percent", on ? toInt(setting(c, "bf_percent")) : 0);
        m.put("ends", ends);
        return m;
    }
    // قیمت نهایی = بیشترین بین تخفیف اختصاصی محصول و تخفیف بلک فرایدی
    static Map<String, Object> price(Map<String, Object> p, int bfPct) {
        int base = toInt(p.get("price"));
        int pct = Math.max(toInt(p.get("discount_percent")), bfPct);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price", base);
        m.put("percent", pct);
        m.put("final", (int) Math.round(base * (100 - pct) / 100.0));
        return m;
    }
    static Map<String, Object> productView(Map<String, Object> p, int bfPct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", toInt(p.get("id")));
        m.put("title", str(p.get("title")));
        m.put("category", str(p.get("category")));
        m.put("description", str(p.get("description")));
        m.put("image", p.get("image"));
        m.put("stock", toInt(p.get("stock")));
        m.put("is_active", toInt(p.get("is_active")));
        m.put("sizes", csv(p.get("sizes")));
        m.put("colors", csv(p.get("colors")));
        m.put("price", price(p, bfPct));
        return m;
    }

    // ---------- auth ----------
    static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.matches("Bearer [a-f0-9]{64}") ? h.substring(7) : null;
    }
    static Map<String, Object> user(Ctx x) throws SQLException {
        String t = bearer(x.ex());
        return t == null ? null : one(x.c(),
            "SELECT u.id, u.name, u.email, u.is_admin FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?", t);
    }
    static Map<String, Object> needUser(Ctx x) throws SQLException {
        Map<String, Object> u = user(x);
        if (u == null) throw new ApiError(401, "ابتدا وارد شوید");
        return u;
    }
    static void needAdmin(Ctx x) throws SQLException {
        if (!flag(needUser(x).get("is_admin"))) throw new ApiError(403, "دسترسی ندارید");
    }
    static Map<String, Object> startSession(Connection c, long uid) throws SQLException {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        String token = HexFormat.of().formatHex(b);
        exec(c, "INSERT INTO sessions (token, user_id) VALUES (?, ?)", token, uid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("user", one(c, "SELECT id, name, email, is_admin FROM users WHERE id = ?", uid));
        return m;
    }

    // ---------- public routes ----------
    static Object productsRoute(Ctx x) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE is_active = 1");
        List<Object> args = new ArrayList<>();
        if (!x.q("cat").isEmpty()) { sql.append(" AND category = ?"); args.add(x.q("cat")); }
        if (!x.q("q").isEmpty()) { sql.append(" AND title LIKE ?"); args.add("%" + x.q("q") + "%"); }
        sql.append(" ORDER BY id DESC");
        int bfPct = toInt(bf(x.c()).get("percent"));
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> p : query(x.c(), sql.toString(), args.toArray())) out.add(productView(p, bfPct));
        return out;
    }

    static Object productRoute(Ctx x) throws SQLException {
        Map<String, Object> p = one(x.c(), "SELECT * FROM products WHERE id = ? AND is_active = 1", toInt(x.q("id")));
        if (p == null) throw new ApiError(404, "محصول پیدا نشد");
        return productView(p, toInt(bf(x.c()).get("percent")));
    }

    static Object registerRoute(Ctx x) throws SQLException {
        Map<String, Object> in = x.in();
        String name = str(in.get("name"));
        String email = str(in.get("email")).toLowerCase();
        String pass = raw(in.get("password"));
        if (name.length() < 3) throw new ApiError(400, "نام را وارد کنید");
        if (!EMAIL.matcher(email).matches()) throw new ApiError(400, "ایمیل معتبر نیست");
        if (pass.length() < 6) throw new ApiError(400, "رمز عبور باید حداقل ۶ کاراکتر باشد");
        if (one(x.c(), "SELECT 1 FROM users WHERE email = ?", email) != null)
            throw new ApiError(400, "این ایمیل قبلا ثبت شده است");
        long uid = insert(x.c(), "INSERT INTO users (name, email, phone, password) VALUES (?,?,?,?)",
                name, email, str(in.get("phone")), BCrypt.hashpw(pass, BCrypt.gensalt()));
        return startSession(x.c(), uid);
    }

    static Object loginRoute(Ctx x) throws SQLException {
        Map<String, Object> in = x.in();
        Map<String, Object> u = one(x.c(), "SELECT * FROM users WHERE email = ?", str(in.get("email")).toLowerCase());
        if (u == null || !BCrypt.checkpw(raw(in.get("password")), str(u.get("password"))))
            throw new ApiError(401, "ایمیل یا رمز عبور اشتباه است");
        return startSession(x.c(), toInt(u.get("id")));
    }

    static Object logoutRoute(Ctx x) throws SQLException {
        String t = bearer(x.ex());
        if (t != null) exec(x.c(), "DELETE FROM sessions WHERE token = ?", t);
        return Map.of("ok", true);
    }

    static Object couponRoute(Ctx x) throws SQLException {
        Map<String, Object> c = one(x.c(), "SELECT code, percent FROM coupons WHERE code = ? AND is_active = 1",
                str(x.in().get("code")).toUpperCase());
        if (c == null) throw new ApiError(400, "کد تخفیف معتبر نیست");
        return c;
    }

    static Object ordersRoute(Ctx x) throws SQLException {
        Map<String, Object> u = needUser(x);
        return query(x.c(), "SELECT id, created_at, total, status FROM orders WHERE user_id = ? ORDER BY id DESC", toInt(u.get("id")));
    }

    static Object checkoutRoute(Ctx x) throws SQLException {
        Map<String, Object> u = needUser(x);
        Map<String, Object> in = x.in();
        Object rawItems = in.get("items");
        List<?> items = rawItems instanceof List<?> lst ? lst : List.of();
        String name = str(in.get("full_name"));
        String phone = str(in.get("phone"));
        String addr = str(in.get("address"));
        if (items.isEmpty()) throw new ApiError(400, "سبد خرید خالی است");
        if (name.length() < 3) throw new ApiError(400, "نام را وارد کنید");
        if (!phone.matches("09\\d{9}")) throw new ApiError(400, "شماره موبایل معتبر نیست");
        if (addr.length() < 10) throw new ApiError(400, "آدرس را کامل وارد کنید");

        Connection c = x.c();
        int bfPct = toInt(bf(c).get("percent"));
        c.setAutoCommit(false);
        try {
            List<Line> lines = new ArrayList<>();
            int subtotal = 0;
            for (Object o : items) {
                Map<?, ?> it = (Map<?, ?>) o;
                Map<String, Object> p = one(c, "SELECT * FROM products WHERE id = ? AND is_active = 1 FOR UPDATE", toInt(it.get("id")));
                if (p == null) throw new ApiError(400, "محصولی در سبد معتبر نیست");
                Map<String, Object> view = productView(p, bfPct);
                int qty = Math.max(1, Math.min(10, toInt(it.get("qty"))));
                String size = str(it.get("size"));
                String color = str(it.get("color"));
                if (!((List<?>) view.get("sizes")).contains(size) || !((List<?>) view.get("colors")).contains(color))
                    throw new ApiError(400, "سایز یا رنگ «" + p.get("title") + "» معتبر نیست");
                if (toInt(p.get("stock")) < qty) throw new ApiError(400, "موجودی «" + p.get("title") + "» کافی نیست");
                int unit = toInt(((Map<?, ?>) view.get("price")).get("final"));
                subtotal += unit * qty;
                lines.add(new Line(p, size, color, qty, unit));
            }

            int pct = 0;
            String code = null;
            String couponIn = str(in.get("coupon")).toUpperCase();
            if (!couponIn.isEmpty()) {
                Map<String, Object> cp = one(c, "SELECT code, percent FROM coupons WHERE code = ? AND is_active = 1", couponIn);
                if (cp != null) { pct = toInt(cp.get("percent")); code = str(cp.get("code")); }
            }
            int discount = (int) Math.round(subtotal * pct / 100.0);
            int total = subtotal - discount;

            long oid = insert(c, "INSERT INTO orders (user_id, full_name, phone, address, subtotal, discount, total, coupon) VALUES (?,?,?,?,?,?,?,?)",
                    toInt(u.get("id")), name, phone, addr, subtotal, discount, total, code);
            for (Line l : lines) {
                exec(c, "INSERT INTO order_items (order_id, product_id, title, size, color, unit_price, qty) VALUES (?,?,?,?,?,?,?)",
                        oid, toInt(l.p().get("id")), l.p().get("title"), l.size(), l.color(), l.unit(), l.qty());
                exec(c, "UPDATE products SET stock = stock - ? WHERE id = ?", l.qty(), toInt(l.p().get("id")));
            }
            c.commit();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("order_id", oid);
            res.put("total", total);
            return res;
        } catch (ApiError e) {
            c.rollback();
            throw e;
        } catch (SQLException e) {
            c.rollback();
            throw new ApiError(409, "خطا در ثبت سفارش");
        } finally {
            c.setAutoCommit(true);
        }
    }

    // ---------- admin routes ----------
    static Object adminDataRoute(Ctx x) throws SQLException {
        needAdmin(x);
        Connection c = x.c();
        List<Object> products = new ArrayList<>();
        for (Map<String, Object> p : query(c, "SELECT * FROM products ORDER BY id DESC")) products.add(productView(p, 0));
        String ends = raw(setting(c, "bf_ends"));
        Map<String, Object> bfMap = new LinkedHashMap<>();
        bfMap.put("active", "1".equals(setting(c, "bf_active")));
        bfMap.put("percent", toInt(setting(c, "bf_percent")));
        bfMap.put("ends", ends.length() > 16 ? ends.substring(0, 16) : ends);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("products", products);
        out.put("orders", query(c, "SELECT id, full_name, phone, address, total, coupon, status, created_at FROM orders ORDER BY id DESC LIMIT 100"));
        out.put("coupons", query(c, "SELECT * FROM coupons ORDER BY id DESC"));
        out.put("bf", bfMap);
        return out;
    }

    static Object adminProductRoute(Ctx x) throws SQLException {
        needAdmin(x);
        Map<String, Object> in = x.in();
        int id = toInt(in.get("id"));
        if (flag(in.get("delete"))) {
            exec(x.c(), "DELETE FROM products WHERE id = ?", id);
            return Map.of("ok", true);
        }
        String title = str(in.get("title"));
        int price = toInt(in.get("price"));
        if (title.isEmpty() || price <= 0) throw new ApiError(400, "عنوان و قیمت را وارد کنید");
        String image = str(in.get("image"));
        Object[] vals = {
            title, str(in.get("category")), raw(in.get("description")).trim(), price,
            Math.min(90, Math.max(0, toInt(in.get("discount_percent")))),
            str(in.get("sizes")), str(in.get("colors")), Math.max(0, toInt(in.get("stock"))),
            image.isEmpty() ? null : image, flag(in.get("is_active")) ? 1 : 0
        };
        if (id > 0) {
            Object[] args = Arrays.copyOf(vals, vals.length + 1);
            args[vals.length] = id;
            exec(x.c(), "UPDATE products SET title=?, category=?, description=?, price=?, discount_percent=?, sizes=?, colors=?, stock=?, image=?, is_active=? WHERE id=?", args);
        } else {
            exec(x.c(), "INSERT INTO products (title, category, description, price, discount_percent, sizes, colors, stock, image, is_active) VALUES (?,?,?,?,?,?,?,?,?,?)", vals);
        }
        return Map.of("ok", true);
    }

    static Object adminOrderRoute(Ctx x) throws SQLException {
        needAdmin(x);
        Map<String, Object> in = x.in();
        String status = str(in.get("status"));
        if (!List.of("pending", "processing", "shipped", "delivered", "cancelled").contains(status))
            throw new ApiError(400, "وضعیت نامعتبر است");
        exec(x.c(), "UPDATE orders SET status = ? WHERE id = ?", status, toInt(in.get("id")));
        return Map.of("ok", true);
    }

    static Object adminSettingsRoute(Ctx x) throws SQLException {
        needAdmin(x);
        Map<String, Object> in = x.in();
        Connection c = x.c();
        exec(c, "REPLACE INTO settings (k, v) VALUES (?, ?)", "bf_active", flag(in.get("active")) ? "1" : "0");
        exec(c, "REPLACE INTO settings (k, v) VALUES (?, ?)", "bf_percent", String.valueOf(Math.min(90, Math.max(0, toInt(in.get("percent"))))));
        String ends = str(in.get("ends"));
        exec(c, "REPLACE INTO settings (k, v) VALUES (?, ?)", "bf_ends", ends.isEmpty() ? null : ends);
        return Map.of("ok", true);
    }

    static Object adminCouponRoute(Ctx x) throws SQLException {
        needAdmin(x);
        Map<String, Object> in = x.in();
        if (flag(in.get("delete"))) {
            exec(x.c(), "DELETE FROM coupons WHERE id = ?", toInt(in.get("id")));
        } else {
            String code = str(in.get("code")).toUpperCase();
            if (code.isEmpty()) throw new ApiError(400, "کد را وارد کنید");
            exec(x.c(), "INSERT IGNORE INTO coupons (code, percent) VALUES (?, ?)",
                    code, Math.min(90, Math.max(1, toInt(in.get("percent")))));
        }
        return Map.of("ok", true);
    }
}
