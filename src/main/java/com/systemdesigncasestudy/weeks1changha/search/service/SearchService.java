package com.systemdesigncasestudy.weeks1changha.search.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.service.BusinessService;
import com.systemdesigncasestudy.weeks1changha.geo.GeoDistance;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.RedisGeoIndexRepository;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbyBusinessItem;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbySearchResponse;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchService.class);

    private final RedisGeoIndexRepository redisGeoIndexRepository;
    private final BusinessService businessService;
    private final int defaultLimit;
    private final int maxLimit;
    private final Timer searchLatencyTimer;
    private final DistributionSummary candidateCountSummary;
    private final DistributionSummary resultCountSummary;

    public SearchService(
            RedisGeoIndexRepository redisGeoIndexRepository,
            BusinessService businessService,
            MeterRegistry meterRegistry,
            @Value("${app.search.default-limit:20}") int defaultLimit,
            @Value("${app.search.max-limit:100}") int maxLimit) {
        this.redisGeoIndexRepository = redisGeoIndexRepository;
        this.businessService = businessService;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.searchLatencyTimer = Timer.builder("proximity.search.latency")
                .description("Latency for nearby search requests")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.candidateCountSummary = DistributionSummary.builder("proximity.search.candidate.count")
                .description("Number of candidate business IDs from Redis GEOSEARCH")
                .register(meterRegistry);
        this.resultCountSummary = DistributionSummary.builder("proximity.search.result.count")
                .description("Number of businesses returned after filtering")
                .register(meterRegistry);
    }

    public NearbySearchResponse searchNearby(
            double latitude,
            double longitude,
            int radius,
            Integer limit,
            String cursor) {
        log.debug("Search request: lat={}, lon={}, radius={}", latitude, longitude, radius);
        Timer.Sample sample = Timer.start();
        try {
            int resolvedLimit = resolveLimit(limit);
            int offset = decodeCursor(cursor);

            // Redis GEOSEARCH: returns IDs sorted by distance ascending
            List<Long> sortedCandidateIds = redisGeoIndexRepository.findByRadius(
                    latitude, longitude, radius);

            log.debug("Redis GEOSEARCH returned {} candidates", sortedCandidateIds.size());
            candidateCountSummary.record(sortedCandidateIds.size());

            int total = sortedCandidateIds.size();
            resultCountSummary.record(total);

            if (total == 0 || offset >= total) {
                return new NearbySearchResponse(total, radius, null, List.of());
            }

            // Only fetch business details for the current page (not all candidates)
            int end = Math.min(offset + resolvedLimit, total);
            List<Long> pageIds = sortedCandidateIds.subList(offset, end);

            List<Business> businesses = businessService.findAllActiveByIds(pageIds);

            // Build response maintaining Redis distance order
            Map<Long, Business> businessMap = new HashMap<>();
            for (Business b : businesses) {
                businessMap.put(b.id(), b);
            }

            List<NearbyBusinessItem> items = new ArrayList<>();
            for (Long id : pageIds) {
                Business business = businessMap.get(id);
                if (business == null)
                    continue;
                double distance = GeoDistance.haversineMeters(
                        latitude, longitude,
                        business.latitude(), business.longitude());
                items.add(new NearbyBusinessItem(
                        business.id(),
                        business.name(),
                        business.category(),
                        Math.round(distance),
                        business.latitude(),
                        business.longitude()));
            }

            String nextCursor = end < total ? encodeCursor(end) : null;
            return new NearbySearchResponse(total, radius, nextCursor, items);
        } finally {
            sample.stop(searchLatencyTimer);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return defaultLimit;
        }
        if (limit <= 0 || limit > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        return limit;
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(decoded);
            if (offset < 0) {
                throw new IllegalArgumentException("cursor must be positive");
            }
            return offset;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }

    private String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }

}
