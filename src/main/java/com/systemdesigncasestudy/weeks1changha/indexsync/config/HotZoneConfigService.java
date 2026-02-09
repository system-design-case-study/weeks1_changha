package com.systemdesigncasestudy.weeks1changha.indexsync.config;

import com.systemdesigncasestudy.weeks1changha.indexsync.repository.MysqlHotZoneConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HotZoneConfigService {

    private static final Logger log = LoggerFactory.getLogger(HotZoneConfigService.class);

    private final MysqlHotZoneConfigRepository repository;
    private final Map<String, HotZoneConfig> configCache = new ConcurrentHashMap<>();

    public HotZoneConfigService(MysqlHotZoneConfigRepository repository) {
        this.repository = repository;
        refreshConfig(); // Initial load
    }

    @Scheduled(fixedRate = 60000) // Refresh every minute
    public void refreshConfig() {
        try {
            List<HotZoneConfig> configs = repository.findAllActive();
            Map<String, HotZoneConfig> newCache = configs.stream()
                    .collect(Collectors.toMap(HotZoneConfig::geohashPrefix, c -> c));

            configCache.clear();
            configCache.putAll(newCache);
            log.info("Refreshed Hot Zone Configs. Count: {}", configCache.size());
        } catch (Exception e) {
            log.error("Failed to refresh Hot Zone Configs", e);
        }
    }

    public Optional<HotZoneConfig> findConfig(String geohash) {
        if (geohash == null || geohash.length() < 4) {
            return Optional.empty();
        }
        // Check for longest matching prefix first if needed,
        // but currently we assume strictly 4-char prefix based on current design.
        // For future flexibility, we can iterate prefixes of length 4, 5, 6...

        // Simple implementation: check 4-char prefix
        String prefix = geohash.substring(0, 4);
        return Optional.ofNullable(configCache.get(prefix));
    }

    public boolean isHotZone(String geohash) {
        return findConfig(geohash).isPresent();
    }
}
