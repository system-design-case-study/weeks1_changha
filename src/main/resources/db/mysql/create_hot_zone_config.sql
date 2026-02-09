CREATE TABLE IF NOT EXISTS hot_zone_config (
    geohash_prefix VARCHAR(10) NOT NULL PRIMARY KEY COMMENT 'Hot Zone Prefix (e.g. wydm)',
    description VARCHAR(255) COMMENT 'Description (e.g. Gangnam Station Area)',
    radius_limit INT DEFAULT 500 COMMENT 'Max Search Radius Limit in Meters',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Whether this rule is active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial Data for Gangnam
INSERT IGNORE INTO hot_zone_config (geohash_prefix, description, radius_limit, is_active)
VALUES ('wydm', 'Gangnam Station Hot Zone', 500, TRUE);
