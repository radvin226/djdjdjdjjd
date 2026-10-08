CREATE DATABASE IF NOT EXISTS pooshak_mehraboo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pooshak_mehraboo;

CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  phone VARCHAR(20),
  password VARCHAR(255) NOT NULL,
  is_admin TINYINT(1) DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  category VARCHAR(50),
  description TEXT,
  price INT NOT NULL,
  discount_percent TINYINT DEFAULT 0,
  sizes VARCHAR(200),
  colors VARCHAR(200),
  stock INT DEFAULT 0,
  image VARCHAR(255) NULL,
  is_active TINYINT(1) DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupons (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  percent TINYINT NOT NULL,
  is_active TINYINT(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  full_name VARCHAR(100),
  phone VARCHAR(20),
  address TEXT,
  subtotal INT,
  discount INT,
  total INT,
  coupon VARCHAR(50) NULL,
  status ENUM('pending','processing','shipped','delivered','cancelled') DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  product_id INT NOT NULL,
  title VARCHAR(200),
  size VARCHAR(20),
  color VARCHAR(50),
  unit_price INT,
  qty INT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settings (
  k VARCHAR(50) PRIMARY KEY,
  v VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO settings (k,v) VALUES ('bf_active','1'),('bf_percent','30'),('bf_ends','2026-11-30 23:59:59');

INSERT INTO coupons (code,percent) VALUES ('MEHRABOO10',10);

INSERT INTO products (title,category,description,price,discount_percent,sizes,colors,stock) VALUES
('مانتو کرپ زنانه','زنانه','مانتو کرپ با پارچه‌ای روان و دوخت دقیق، مناسب فصل‌های گرم و معتدل.',1450000,10,'S,M,L,XL','مشکی,سرمه‌ای,خاکی',20),
('تی‌شرت پنبه‌ای مردانه','مردانه','تی‌شرت ۱۰۰٪ پنبه‌ای با یقه گرد و برش راحت.',390000,0,'M,L,XL,XXL','سفید,مشکی,طوسی',35),
('ست بچگانه پاییزه','بچگانه','ست دو تکه نرم و گرم، مناسب فصل سرد.',620000,15,'2,4,6,8','صورتی,آبی',15),
('شلوار جین راحتی زنانه','زنانه','جین کش‌دار با برش راحت برای استفاده روزمره.',890000,0,'26,27,28,29,30','آبی روشن,آبی تیره',12);

CREATE TABLE sessions (
  token CHAR(64) PRIMARY KEY,
  user_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
