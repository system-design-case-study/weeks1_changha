package com.systemdesigncasestudy.weeks1changha.search.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.service.BusinessService;
import com.systemdesigncasestudy.weeks1changha.cache.GeoCellCache;
import com.systemdesigncasestudy.weeks1changha.geo.GeoDistance;
import com.systemdesigncasestudy.weeks1changha.geo.GeohashUtils;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.GeohashIndexRepository;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbyBusinessItem;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbySearchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final GeohashIndexRepository geohashIndexRepository;
    private final BusinessService businessService;
    private final GeoCellCache geoCellCache;
    private final int defaultLimit;
    private final int maxLimit;
    private final Timer searchLatencyTimer;
    private final Counter geoCacheHitCounter;
    private final Counter geoCacheMissCounter;
    private final DistributionSummary candidateCountSummary;
    private final DistributionSummary resultCountSummary;

    public SearchService(
            GeohashIndexRepository geohashIndexRepository,
            BusinessService businessService,
            GeoCellCache geoCellCache,
            MeterRegistry meterRegistry,
            @Value("${app.search.default-limit:20}") int defaultLimit,
            @Value("${app.search.max-limit:100}") int maxLimit) {
        this.geohashIndexRepository = geohashIndexRepository;
        this.businessService = businessService;
        this.geoCellCache = geoCellCache;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.searchLatencyTimer = Timer.builder("proximity.search.latency")
                .description("Latency for nearby search requests")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.geoCacheHitCounter = Counter.builder("proximity.search.geo.cache.hit")
                .description("Geo-cell cache hits")
                .register(meterRegistry);
        this.geoCacheMissCounter = Counter.builder("proximity.search.geo.cache.miss")
                .description("Geo-cell cache misses")
                .register(meterRegistry);
        this.candidateCountSummary = DistributionSummary.builder("proximity.search.candidate.count")
                .description("Number of candidate business IDs gathered before distance filtering")
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
        Timer.Sample sample = Timer.start();
        try {
            int resolvedLimit = resolveLimit(limit);
            int offset = decodeCursor(cursor);

            int precision = precisionForRadius(radius);
            Set<String> geohashes = GeohashUtils.centerAndNeighbors(latitude, longitude, precision);
            Set<Long> candidateBusinessIds = new LinkedHashSet<>();

            for (String geohash : geohashes) {
                String cacheKey = "geo:" + precision + ":" + geohash;
                Optional<List<Long>> cachedIds = geoCellCache.get(cacheKey);
                List<Long> ids;
                if (cachedIds.isPresent()) {
                    geoCacheHitCounter.increment();
                    ids = cachedIds.get();
                } else {
                    geoCacheMissCounter.increment();
                    ids = new ArrayList<>(geohashIndexRepository.findBusinessIdsByPrefix(geohash));
                    geoCellCache.put(cacheKey, ids);
                }
                candidateBusinessIds.addAll(ids);
            }

            candidateCountSummary.record(candidateBusinessIds.size());
            List<SearchCandidate> filtered = new ArrayList<>();
            List<Business> businesses = businessService.findAllActiveByIds(candidateBusinessIds);

            for (Business business : businesses) {

                double distance = GeoDistance.haversineMeters(
                        latitude,
                        longitude,
                        business.latitude(),
                        business.longitude());
                if (distance <= radius) {
                    filtered.add(new SearchCandidate(business, distance));
                }
            }

            filtered.sort(Comparator.comparingDouble(SearchCandidate::distanceMeters));
            int total = filtered.size();
            resultCountSummary.record(total);
            if (offset >= total) {
                return new NearbySearchResponse(total, null, List.of());
            }

            int end = Math.min(offset + resolvedLimit, total);
            List<NearbyBusinessItem> items = new ArrayList<>();
            for (int i = offset; i < end; i++) {
                SearchCandidate candidate = filtered.get(i);
                Business business = candidate.business();
                items.add(new NearbyBusinessItem(
                        business.id(),
                        business.name(),
                        business.category(),
                        Math.round(candidate.distanceMeters()),
                        business.latitude(),
                        business.longitude()));
            }

            String nextCursor = end < total ? encodeCursor(end) : null;
            return new NearbySearchResponse(total, nextCursor, items);
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

    private int precisionForRadius(int radiusMeters) {
        if (radiusMeters <= 300) {
            return 7;
        }
        if (radiusMeters <= 1_000) {
            return 6;
        }
        if (radiusMeters <= 5_000) {
            return 5;
        }
        if (radiusMeters <= 20_000) {
            return 4;
        }
        return 3;
    }

    private record SearchCandidate(Business business, double distanceMeters) {
    }
}
