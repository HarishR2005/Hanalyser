-- Hanalyser Database Schema
-- Run this if Hibernate DDL auto doesn't create tables, or for fresh setup
-- Usage: mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS arthatrack_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE arthatrack_db;

-- ── Users ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  email      VARCHAR(100) NOT NULL UNIQUE,
  name       VARCHAR(100) NOT NULL,
  password   VARCHAR(255) NOT NULL,
  role       ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  enabled    TINYINT(1) NOT NULL DEFAULT 0,
  verified   TINYINT(1) NOT NULL DEFAULT 0,
  otp_code   VARCHAR(10),
  otp_expiry DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  last_login DATETIME,
  INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Stock Analysis History ────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_analysis_history (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  ticker           VARCHAR(20) NOT NULL,
  company_name     VARCHAR(200),
  current_price    DOUBLE,
  fair_value       DOUBLE,
  intrinsic_value  DOUBLE,
  margin_of_safety DOUBLE,
  pe_ratio         DOUBLE,
  risk_score       DOUBLE,
  valuation        VARCHAR(20),
  recommendation   VARCHAR(10),
  buy_below        DOUBLE,
  sell_above       DOUBLE,
  hold_until       DOUBLE,
  confidence       INT,
  analyzed_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_history_user (user_id),
  INDEX idx_history_ticker (ticker),
  INDEX idx_history_analyzed (analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Watchlist ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS watchlist (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  ticker       VARCHAR(20) NOT NULL,
  company_name VARCHAR(200),
  added_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  target_price DOUBLE,
  notes        VARCHAR(500),
  UNIQUE KEY uq_watchlist_user_ticker (user_id, ticker),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_watchlist_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
