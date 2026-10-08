پوشاک مهربانو — راهنمای کوتاه

ساختار زیپ
- فایل‌های ریشه (index.html، صفحات دیگر، CNAME، .nojekyll، پوشه assets): فرانت‌اند. روی GitHub Pages در ریشه ریپو آپلود شوند.
- backend-php: بک‌اند PHP برای هاست cPanel. index.php و database.sql را در زیردامنه api.mehrbanoopashak.ir بگذارید.
- backend-java: بک‌اند Java برای VPS. با Maven بسازید: mvn clean package
  فقط یکی از دو بک‌اند را استفاده کنید.

نکته امنیتی
پوشه‌های backend-php و backend-java شامل تنظیمات دیتابیس هستند. آن‌ها را روی GitHub عمومی آپلود نکنید؛ فقط فایل‌های ریشه و assets را به ریپو بفرستید.

ادمین
بعد از ثبت‌نام در سایت، این دستور را در phpMyAdmin اجرا کنید:
UPDATE users SET is_admin=1 WHERE email='ایمیل‌شما';
