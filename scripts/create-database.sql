-- 由数据库管理员执行；先替换下列示例密码。不要在真实环境使用此示例密码。
CREATE DATABASE IF NOT EXISTS smart_knowledge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER IF NOT EXISTS 'smartknowledge'@'localhost' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
GRANT ALL PRIVILEGES ON smart_knowledge_db.* TO 'smartknowledge'@'localhost';
-- 集成测试单独建立 smart_knowledge_test，并授予测试账号该库权限。
