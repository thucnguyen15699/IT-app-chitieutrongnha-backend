-- MySQL 8 schema and local bootstrap data for NhaMinh.
-- Spring Boot runs this file on startup. It is intentionally non-destructive.

CREATE DATABASE IF NOT EXISTS `chitieugiadinh`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `chitieugiadinh`;

CREATE TABLE IF NOT EXISTS `families` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(120) NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `email` VARCHAR(254) DEFAULT NULL,
  `phone_number` VARCHAR(30) DEFAULT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `email_verified` BOOLEAN NOT NULL DEFAULT TRUE,
  `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
  `created_at` TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `users` MODIFY COLUMN `email` VARCHAR(254) DEFAULT NULL;
SET @user_column_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'users'
     AND `column_name` = 'username') = 0,
  'ALTER TABLE `users` ADD COLUMN `username` VARCHAR(50) DEFAULT NULL',
  'SELECT 1'
);
PREPARE `add_user_column_statement` FROM @user_column_sql;
EXECUTE `add_user_column_statement`;
DEALLOCATE PREPARE `add_user_column_statement`;

SET @user_column_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'users'
     AND `column_name` = 'phone_number') = 0,
  'ALTER TABLE `users` ADD COLUMN `phone_number` VARCHAR(30) DEFAULT NULL',
  'SELECT 1'
);
PREPARE `add_user_column_statement` FROM @user_column_sql;
EXECUTE `add_user_column_statement`;
DEALLOCATE PREPARE `add_user_column_statement`;

SET @user_column_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'users'
     AND `column_name` = 'email_verified') = 0,
  'ALTER TABLE `users` ADD COLUMN `email_verified` BOOLEAN NOT NULL DEFAULT TRUE',
  'SELECT 1'
);
PREPARE `add_user_column_statement` FROM @user_column_sql;
EXECUTE `add_user_column_statement`;
DEALLOCATE PREPARE `add_user_column_statement`;

SET @user_column_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'users'
     AND `column_name` = 'enabled') = 0,
  'ALTER TABLE `users` ADD COLUMN `enabled` BOOLEAN NOT NULL DEFAULT TRUE',
  'SELECT 1'
);
PREPARE `add_user_column_statement` FROM @user_column_sql;
EXECUTE `add_user_column_statement`;
DEALLOCATE PREPARE `add_user_column_statement`;

UPDATE `users`
SET `username` = CASE
  WHEN `email` = 'admin@chitieugiadinh.local' THEN 'admin'
  ELSE CONCAT('user', `id`)
END
WHERE `username` IS NULL OR `username` = '';

UPDATE `users`
SET `email` = NULL, `email_verified` = TRUE, `enabled` = TRUE, `name` = 'Administrator'
WHERE `username` = 'admin';

ALTER TABLE `users` MODIFY COLUMN `username` VARCHAR(50) NOT NULL;
SET @username_index_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`statistics`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'users'
     AND `index_name` = 'uk_users_username') = 0,
  'ALTER TABLE `users` ADD UNIQUE KEY `uk_users_username` (`username`)',
  'SELECT 1'
);
PREPARE `username_index_statement` FROM @username_index_sql;
EXECUTE `username_index_statement`;
DEALLOCATE PREPARE `username_index_statement`;

CREATE TABLE IF NOT EXISTS `family_memberships` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(30) NOT NULL,
  `joined_at` TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_member` (`family_id`, `user_id`),
  KEY `idx_family_memberships_user` (`user_id`),
  CONSTRAINT `fk_family_memberships_family`
    FOREIGN KEY (`family_id`) REFERENCES `families` (`id`),
  CONSTRAINT `fk_family_memberships_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `roles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255) NOT NULL,
  `priority` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permissions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(80) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `module` VARCHAR(60) NOT NULL,
  `description` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_user_roles_role` (`role_id`),
  CONSTRAINT `fk_user_roles_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_role`
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_permissions` (
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`),
  KEY `idx_role_permissions_permission` (`permission_id`),
  CONSTRAINT `fk_role_permissions_role`
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permissions_permission`
    FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `email_verification_tokens` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `token_hash` VARCHAR(64) NOT NULL,
  `expires_at` TIMESTAMP(6) NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_verification_user` (`user_id`),
  UNIQUE KEY `uk_email_verification_token_hash` (`token_hash`),
  CONSTRAINT `fk_email_verification_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `email_verification_tokens` MODIFY COLUMN `token_hash` VARCHAR(64) NOT NULL;

CREATE TABLE IF NOT EXISTS `expense_categories` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT DEFAULT NULL,
  `name` VARCHAR(100) NOT NULL,
  `type` ENUM('EXPENSE', 'INCOME') NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_expense_categories_family` (`family_id`),
  CONSTRAINT `fk_expense_categories_family`
    FOREIGN KEY (`family_id`) REFERENCES `families` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('C4826E2075E1BB916E67') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = 'C384C2826E2075C3A1C2BBC2916E67';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('C49069206368E1BBA3') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = 'C384C29069206368C3A1C2BBC2A3';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('44692063687579E1BB836E') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '44692063687579C3A1C2BBC2836E';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('4E68C3A02063E1BBAD61') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '4E68C383C2A02063C3A1C2BBC2AD61';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('4769C3A16F2064E1BBA563') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '4769C383C2A16F2064C3A1C2BBC2A563';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('592074E1BABF') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '592074C3A1C2BAC2BF';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('4D75612073E1BAAF6D') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '4D75612073C3A1C2BAC2AF6D';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('4CC6B0C6A16E67') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '4CC386C2B0C386C2A16E67';
UPDATE `expense_categories` SET `name` = CONVERT(UNHEX('546875206E68E1BAAD70206B68C3A163') USING utf8mb4)
WHERE `family_id` IS NULL AND HEX(`name`) = '546875206E68C3A1C2BAC2AD70206B68C383C2A163';

CREATE TABLE IF NOT EXISTS `family_transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `category_id` BIGINT DEFAULT NULL,
  `created_by` BIGINT NOT NULL,
  `type` ENUM('EXPENSE', 'INCOME') NOT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `occurred_at` DATETIME(6) NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_transactions_family_date` (`family_id`, `occurred_at`),
  KEY `idx_transactions_category` (`category_id`),
  KEY `idx_transactions_creator` (`created_by`),
  CONSTRAINT `chk_transactions_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `fk_transactions_family`
    FOREIGN KEY (`family_id`) REFERENCES `families` (`id`),
  CONSTRAINT `fk_transactions_category`
    FOREIGN KEY (`category_id`) REFERENCES `expense_categories` (`id`),
  CONSTRAINT `fk_transactions_creator`
    FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `family_transactions` MODIFY COLUMN `occurred_at` DATETIME(6) NOT NULL;

-- Đối tượng hưởng khoản chi (cả nhà, bố, mẹ, con...). family_id NULL = mặc định cho mọi gia đình.
CREATE TABLE IF NOT EXISTS `spending_targets` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT DEFAULT NULL,
  `name` VARCHAR(60) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 100,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_spending_targets_family` (`family_id`),
  CONSTRAINT `fk_spending_targets_family`
    FOREIGN KEY (`family_id`) REFERENCES `families` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `spending_targets` (`family_id`, `name`, `sort_order`)
SELECT NULL, `seed`.`name`, `seed`.`sort_order`
FROM (
  SELECT 'Cả nhà' AS `name`, 0 AS `sort_order`
  UNION ALL SELECT 'Bố', 10
  UNION ALL SELECT 'Mẹ', 20
  UNION ALL SELECT 'Con', 30
) AS `seed`
WHERE NOT EXISTS (
  SELECT 1 FROM `spending_targets` AS `existing`
  WHERE `existing`.`family_id` IS NULL
    AND `existing`.`name` = `seed`.`name`
);

SET @target_column_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'family_transactions'
     AND `column_name` = 'target_id') = 0,
  'ALTER TABLE `family_transactions` ADD COLUMN `target_id` BIGINT DEFAULT NULL AFTER `category_id`',
  'SELECT 1'
);
PREPARE `add_target_column_statement` FROM @target_column_sql;
EXECUTE `add_target_column_statement`;
DEALLOCATE PREPARE `add_target_column_statement`;

SET @target_fk_sql = IF(
  (SELECT COUNT(*) FROM `information_schema`.`table_constraints`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'family_transactions'
     AND `constraint_name` = 'fk_transactions_target') = 0,
  'ALTER TABLE `family_transactions` ADD CONSTRAINT `fk_transactions_target` FOREIGN KEY (`target_id`) REFERENCES `spending_targets` (`id`)',
  'SELECT 1'
);
PREPARE `add_target_fk_statement` FROM @target_fk_sql;
EXECUTE `add_target_fk_statement`;
DEALLOCATE PREPARE `add_target_fk_statement`;

-- Khoản đã biết trước sẽ phải chi trong tương lai.
CREATE TABLE IF NOT EXISTS `planned_expenses` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `created_by` BIGINT NOT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `description` VARCHAR(500) NOT NULL,
  `due_on` DATE NOT NULL,
  `status` ENUM('PENDING', 'PAID') NOT NULL DEFAULT 'PENDING',
  `paid_transaction_id` BIGINT DEFAULT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_planned_family_status_due` (`family_id`, `status`, `due_on`),
  CONSTRAINT `chk_planned_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `fk_planned_family` FOREIGN KEY (`family_id`) REFERENCES `families` (`id`),
  CONSTRAINT `fk_planned_category` FOREIGN KEY (`category_id`) REFERENCES `expense_categories` (`id`),
  CONSTRAINT `fk_planned_target` FOREIGN KEY (`target_id`) REFERENCES `spending_targets` (`id`),
  CONSTRAINT `fk_planned_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_planned_transaction` FOREIGN KEY (`paid_transaction_id`) REFERENCES `family_transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mục tiết kiệm (du lịch, học tập, mua nhà...).
CREATE TABLE IF NOT EXISTS `savings_goals` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `purpose` VARCHAR(60) NOT NULL,
  `target_amount` DECIMAL(15, 2) NOT NULL,
  `deadline` DATE DEFAULT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_savings_goals_family` (`family_id`),
  CONSTRAINT `chk_savings_target_positive` CHECK (`target_amount` > 0),
  CONSTRAINT `fk_savings_goals_family` FOREIGN KEY (`family_id`) REFERENCES `families` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Các lần gửi vào / rút ra khỏi mục tiết kiệm.
CREATE TABLE IF NOT EXISTS `savings_contributions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `goal_id` BIGINT NOT NULL,
  `created_by` BIGINT NOT NULL,
  `type` ENUM('DEPOSIT', 'WITHDRAW') NOT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `occurred_on` DATE NOT NULL,
  `note` VARCHAR(255) DEFAULT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_savings_contrib_goal_date` (`goal_id`, `occurred_on`),
  CONSTRAINT `chk_savings_contrib_positive` CHECK (`amount` > 0),
  CONSTRAINT `fk_savings_contrib_goal` FOREIGN KEY (`goal_id`) REFERENCES `savings_goals` (`id`),
  CONSTRAINT `fk_savings_contrib_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lời mời vào gia đình (theo email và/hoặc số điện thoại đã chuẩn hóa).
CREATE TABLE IF NOT EXISTS `family_invitations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `invited_by` BIGINT NOT NULL,
  `email` VARCHAR(254) DEFAULT NULL,
  `phone_number` VARCHAR(30) DEFAULT NULL,
  `message` VARCHAR(255) DEFAULT NULL,
  `status` ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
  `responded_by` BIGINT DEFAULT NULL,
  `responded_at` TIMESTAMP(6) NULL DEFAULT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_invitations_email_status` (`email`, `status`),
  KEY `idx_invitations_phone_status` (`phone_number`, `status`),
  KEY `idx_invitations_family_status` (`family_id`, `status`),
  CONSTRAINT `chk_invitation_has_contact` CHECK (`email` IS NOT NULL OR `phone_number` IS NOT NULL),
  CONSTRAINT `fk_invitations_family` FOREIGN KEY (`family_id`) REFERENCES `families` (`id`),
  CONSTRAINT `fk_invitations_inviter` FOREIGN KEY (`invited_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_invitations_responder` FOREIGN KEY (`responded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `family_budgets` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `category_id` BIGINT DEFAULT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `starts_on` DATE NOT NULL,
  `ends_on` DATE NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_budgets_family_period` (`family_id`, `starts_on`, `ends_on`),
  CONSTRAINT `chk_budgets_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `chk_budgets_period_valid` CHECK (`ends_on` >= `starts_on`),
  CONSTRAINT `fk_budgets_family`
    FOREIGN KEY (`family_id`) REFERENCES `families` (`id`),
  CONSTRAINT `fk_budgets_category`
    FOREIGN KEY (`category_id`) REFERENCES `expense_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `roles` (`code`, `name`, `description`, `priority`) VALUES
  ('ADMIN', 'Quản trị viên', 'Toàn quyền quản trị hệ thống', 1000),
  ('OWNER', 'Chủ gia đình', 'Quản lý thành viên và dữ liệu gia đình', 500),
  ('MEMBER', 'Thành viên', 'Sử dụng các chức năng chi tiêu gia đình', 100);

UPDATE `roles` SET `name` = 'Quản trị viên', `description` = 'Toàn quyền quản trị hệ thống' WHERE `code` = 'ADMIN';
UPDATE `roles` SET `name` = 'Chủ gia đình', `description` = 'Quản lý thành viên và dữ liệu gia đình' WHERE `code` = 'OWNER';
UPDATE `roles` SET `name` = 'Thành viên', `description` = 'Sử dụng các chức năng chi tiêu gia đình' WHERE `code` = 'MEMBER';

INSERT IGNORE INTO `permissions` (`code`, `name`, `module`, `description`) VALUES
  ('USER_READ', 'Xem người dùng', 'USER', 'Xem danh sách và hồ sơ người dùng'),
  ('USER_MANAGE', 'Quản lý người dùng', 'USER', 'Tạo, cập nhật, khóa và xóa người dùng'),
  ('ROLE_MANAGE', 'Quản lý vai trò', 'ACCESS', 'Quản lý vai trò và quyền hệ thống'),
  ('FAMILY_MANAGE', 'Quản lý gia đình', 'FAMILY', 'Quản lý gia đình và thành viên'),
  ('TRANSACTION_READ', 'Xem giao dịch', 'TRANSACTION', 'Xem giao dịch trong gia đình'),
  ('TRANSACTION_MANAGE', 'Quản lý giao dịch', 'TRANSACTION', 'Tạo, sửa và xóa giao dịch'),
  ('CATEGORY_MANAGE', 'Quản lý danh mục', 'CATEGORY', 'Quản lý danh mục thu chi'),
  ('BUDGET_MANAGE', 'Quản lý ngân sách', 'BUDGET', 'Tạo, sửa và xóa ngân sách'),
  ('REPORT_READ', 'Xem báo cáo', 'REPORT', 'Xem báo cáo thu chi');

UPDATE `permissions` SET `name` = 'Xem người dùng', `description` = 'Xem danh sách và hồ sơ người dùng' WHERE `code` = 'USER_READ';
UPDATE `permissions` SET `name` = 'Quản lý người dùng', `description` = 'Tạo, cập nhật, khóa và xóa người dùng' WHERE `code` = 'USER_MANAGE';
UPDATE `permissions` SET `name` = 'Quản lý vai trò', `description` = 'Quản lý vai trò và quyền hệ thống' WHERE `code` = 'ROLE_MANAGE';
UPDATE `permissions` SET `name` = 'Quản lý gia đình', `description` = 'Quản lý gia đình và thành viên' WHERE `code` = 'FAMILY_MANAGE';
UPDATE `permissions` SET `name` = 'Xem giao dịch', `description` = 'Xem giao dịch trong gia đình' WHERE `code` = 'TRANSACTION_READ';
UPDATE `permissions` SET `name` = 'Quản lý giao dịch', `description` = 'Tạo, sửa và xóa giao dịch' WHERE `code` = 'TRANSACTION_MANAGE';
UPDATE `permissions` SET `name` = 'Quản lý danh mục', `description` = 'Quản lý danh mục thu chi' WHERE `code` = 'CATEGORY_MANAGE';
UPDATE `permissions` SET `name` = 'Quản lý ngân sách', `description` = 'Tạo, sửa và xóa ngân sách' WHERE `code` = 'BUDGET_MANAGE';
UPDATE `permissions` SET `name` = 'Xem báo cáo', `description` = 'Xem báo cáo thu chi' WHERE `code` = 'REPORT_READ';

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT `roles`.`id`, `permissions`.`id`
FROM `roles` CROSS JOIN `permissions`
WHERE `roles`.`code` = 'ADMIN';

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT `roles`.`id`, `permissions`.`id`
FROM `roles`
JOIN `permissions` ON `permissions`.`code` IN (
  'FAMILY_MANAGE', 'TRANSACTION_READ', 'TRANSACTION_MANAGE',
  'CATEGORY_MANAGE', 'BUDGET_MANAGE', 'REPORT_READ'
)
WHERE `roles`.`code` = 'OWNER';

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT `roles`.`id`, `permissions`.`id`
FROM `roles`
JOIN `permissions` ON `permissions`.`code` IN (
  'TRANSACTION_READ', 'TRANSACTION_MANAGE', 'REPORT_READ'
)
WHERE `roles`.`code` = 'MEMBER';

INSERT INTO `expense_categories` (`family_id`, `name`, `type`)
SELECT NULL, `seed`.`name`, `seed`.`type`
FROM (
  SELECT 'Ăn uống' AS `name`, 'EXPENSE' AS `type`
  UNION ALL SELECT 'Đi chợ', 'EXPENSE'
  UNION ALL SELECT 'Di chuyển', 'EXPENSE'
  UNION ALL SELECT 'Nhà cửa', 'EXPENSE'
  UNION ALL SELECT 'Y tế', 'EXPENSE'
  UNION ALL SELECT 'Giáo dục', 'EXPENSE'
  UNION ALL SELECT 'Mua sắm', 'EXPENSE'
  UNION ALL SELECT 'Lương', 'INCOME'
  UNION ALL SELECT 'Kinh doanh', 'INCOME'
  UNION ALL SELECT 'Thu nhập khác', 'INCOME'
) AS `seed`
WHERE NOT EXISTS (
  SELECT 1 FROM `expense_categories` AS `existing`
  WHERE `existing`.`family_id` IS NULL
    AND `existing`.`name` = `seed`.`name`
    AND `existing`.`type` = `seed`.`type`
);

INSERT INTO `families` (`name`, `created_at`)
SELECT 'Gia đình quản trị', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM `families` WHERE `name` = 'Gia đình quản trị'
);

UPDATE `families` `family`
JOIN `family_memberships` `membership` ON `membership`.`family_id` = `family`.`id`
JOIN `users` ON `users`.`id` = `membership`.`user_id`
SET `family`.`name` = 'Gia đình quản trị'
WHERE `users`.`username` = 'admin';

SET @admin_family_id = (
  SELECT `id` FROM `families`
  WHERE `name` = 'Gia đình quản trị'
  ORDER BY `id`
  LIMIT 1
);

-- This development-only account has a BCrypt hash; replace the password before deployment.
INSERT INTO `users` (
  `username`, `name`, `email`, `phone_number`, `password_hash`,
  `email_verified`, `enabled`, `created_at`
)
SELECT 'admin', 'Administrator', NULL, NULL,
       '$2a$10$1O6CmUE0eOZwcvrsJz9yX.xppINuagRMgJ13gJjEMaPvQfnIWv8YW',
       TRUE, TRUE, CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM `users` WHERE `username` = 'admin'
);

SET @admin_user_id = (
  SELECT `id` FROM `users` WHERE `username` = 'admin' LIMIT 1
);
UPDATE `users`
SET `email` = NULL, `email_verified` = TRUE, `enabled` = TRUE
WHERE `id` = @admin_user_id;

INSERT IGNORE INTO `family_memberships` (`family_id`, `user_id`, `role`, `joined_at`)
VALUES (@admin_family_id, @admin_user_id, 'OWNER', CURRENT_TIMESTAMP(6));

INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
SELECT @admin_user_id, `id` FROM `roles` WHERE `code` = 'ADMIN';

INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
SELECT `users`.`id`, `roles`.`id`
FROM `users` JOIN `roles` ON `roles`.`code` = 'OWNER'
WHERE `users`.`username` <> 'admin';
