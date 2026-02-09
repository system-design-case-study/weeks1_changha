CREATE TABLE IF NOT EXISTS geohash_index_hot (
    geohash VARCHAR(12) NOT NULL,
    business_id BIGINT NOT NULL,
    PRIMARY KEY (geohash, business_id),
    KEY idx_geohash_hot_biz_id (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
