CREATE TABLE IF NOT EXISTS business_id_sequence (
  id BIGINT NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS business (
  id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  phone VARCHAR(32) NULL,
  address VARCHAR(255) NOT NULL,
  latitude DECIMAL(9, 6) NOT NULL,
  longitude DECIMAL(9, 6) NOT NULL,
  geohash VARCHAR(12) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_business_owner_id (owner_id),
  KEY idx_business_updated_at (updated_at),
  KEY idx_business_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS geohash_index (
  geohash VARCHAR(12) NOT NULL,
  business_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (geohash, business_id),
  KEY idx_geohash_index_business_id (business_id),
  CONSTRAINT fk_geohash_index_business_id
    FOREIGN KEY (business_id) REFERENCES business (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS change_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  business_id BIGINT NOT NULL,
  change_type VARCHAR(16) NOT NULL,
  processed TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_change_log_processed_id (processed, id),
  KEY idx_change_log_processed_created_at (processed, created_at),
  CONSTRAINT fk_change_log_business_id
    FOREIGN KEY (business_id) REFERENCES business (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS geohash_index_hot (
    geohash VARCHAR(12) NOT NULL,
    business_id BIGINT NOT NULL,
    PRIMARY KEY (geohash, business_id),
    KEY idx_geohash_hot_biz_id (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
