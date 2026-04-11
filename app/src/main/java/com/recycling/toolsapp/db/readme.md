-- 添加 INT 类型字段（默认允许 NULL）
ALTER TABLE your_table ADD COLUMN age INT;

-- 添加 DOUBLE 类型字段并设置默认值
ALTER TABLE your_table ADD COLUMN price DOUBLE DEFAULT 0.0;


-- 添加 VARCHAR 类型字段（长度限制）
ALTER TABLE your_table ADD COLUMN name VARCHAR(100) NOT NULL;

-- 添加 TEXT 类型长文本字段
ALTER TABLE your_table ADD COLUMN description TEXT;


-- 添加 DATE 类型字段
ALTER TABLE your_table ADD COLUMN create_date DATE;

-- 添加 TIMESTAMP 类型字段（自动记录时间）
ALTER TABLE your_table ADD COLUMN update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;


ALTER TABLE your_table ADD COLUMN email VARCHAR(255) NOT NULL;

ALTER TABLE your_table ADD COLUMN user_code VARCHAR(50) UNIQUE;


ALTER TABLE your_table ADD COLUMN status VARCHAR(20) DEFAULT 'active';

ALTER TABLE your_table ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY;

ALTER TABLE your_table ADD COLUMN avatar BLOB;

ALTER TABLE your_table ADD COLUMN gender ENUM('male', 'female', 'other');

ALTER TABLE your_table ADD COLUMN metadata JSON;

ALTER TABLE your_table
ADD COLUMN address VARCHAR(200),
ADD COLUMN phone VARCHAR(20) NOT NULL,
ADD COLUMN salary DECIMAL(10,2) DEFAULT 0.0;


ALTER TABLE your_table ADD COLUMN seq_num INT FIRST;


ALTER TABLE your_table ADD COLUMN middle_name VARCHAR(50) AFTER first_name;


