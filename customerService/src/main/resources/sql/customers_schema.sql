-- MySQL schema for customerService `customers` table
-- Compatible with MySQL 8.x
-- Notes:
--  - `id` is a String in the app. If your MySQL supports functional defaults (8.0.13+),
--    you can keep the DEFAULT (uuid()). Otherwise, remove the DEFAULT and let the app
--    populate the id (e.g., with a UUID) before insert.

USE amit;

CREATE TABLE IF NOT EXISTS customers (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(255) NOT NULL,
  email           VARCHAR(255) NOT NULL,
  created_at      BIGINT NOT NULL,
  password_salt   VARCHAR(255) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  UNIQUE (email)
);

CREATE INDEX idx_customers_created_at ON customers (created_at);


-- If your MySQL version does NOT allow DEFAULT (uuid()), run this instead:
-- ALTER TABLE customers MODIFY id CHAR(36) NOT NULL PRIMARY KEY;
-- and ensure the application sets `id` to a UUID string before inserting.


use `amit`;
TRUNCATE `amit`.`order_outbox`;
TRUNCATE `amit`.`orders`;
TRUNCATE `amit`.`customers`;

SELECT * FROM amit.orders;
SELECT * FROM amit.order_outbox;
SELECT * FROM amit.customers;

