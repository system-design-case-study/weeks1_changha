package com.systemdesigncasestudy.weeks1changha.search.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.service.BusinessService;
import com.systemdesigncasestudy.weeks1changha.cache.GeoCellCache;
import com.systemdesigncasestudy.weeks1changha.geo.GeoDistance;
import com.systemdesigncasestudy.weeks1changha.geo.GeohashUtils;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.GeohashIndexRepository;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbyBusinessItem;
import com.systemdesigncasestudy.weeks1changha.search.dto.NearbySearchResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

    public SearchService(
        GeohashIndexRepository geohashIndexRepository,
        BusinessService businessService,
        GeoCellCache geoCellCache,
        @Value("${app.search.default-limit:20}") int defaultLimit,
        @Value("${app.search.max-limit:100}") int maxLimit
    ) {
        this.geohashIndexRepository = geohashIndexRepository;
        this.businessService = businessService;
        this.geoCellCache = geoCellCache;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    public NearbySearchResponse searchNearby(
        double latitude,
        double longitude,
        int radius,
        Integer limit,
        String cursor
    ) {
        int resolvedLimit = resolveLimit(limit);
        int offset = decodeCursor(cursor);

        int precision = precisionForRadius(radius);
        Set<String> geohashes = GeohashUtils.centerAndNeighbors(latitude, longitude, precision);
        Set<Long> candidateBusinessIds = new LinkedHashSet<>();

        for (String geohash : geohashes) {
            String cacheKey = "geo:" + precision + ":" + geohash;
            List<Long> ids = geoCellCache.get(cacheKey)
                .orElseGet(() -> {
                    List<Long> loaded = new ArrayList<>(geohashIndexRepository.findBusinessIdsByPrefix(geohash));
                    geoCellCache.put(cacheKey, loaded);
                    return loaded;
                });
            candidateBusinessIds.addAll(ids);
        }

        List<SearchCandidate> filtered = new ArrayList<>();
        for (Long businessId : candidateBusinessIds) {
            Business business = businessService.findActiveById(businessId).orElse(null);
            if (business == null) {
                continue;
            }

            double distance = GeoDistance.haversineMeters(
                latitude,
                longitude,
                business.latitude(),
                business.longitude()
            );
            if (distance <= radius) {
                filtered.add(new SearchCandidate(business, distance));
            }
        }

        filtered.sort(Comparator.comparingDouble(SearchCandidate::distanceMeters));
        int total = filtered.size();

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
                business.longitude()
            ));
        }

        String nextCursor = end < total ? encodeCursor(end) : null;
        return new NearbySearchResponse(total, nextCursor, items);
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
