package com.systemdesigncasestudy.weeks1changha.business.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.domain.BusinessStatus;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessCreateRequest;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessResponse;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessUpdateRequest;
import com.systemdesigncasestudy.weeks1changha.business.repository.BusinessRepository;
import com.systemdesigncasestudy.weeks1changha.cache.BusinessCache;
import com.systemdesigncasestudy.weeks1changha.common.exception.NotFoundException;
import com.systemdesigncasestudy.weeks1changha.geo.GeohashUtils;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.ChangeLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BusinessService {

    private static final int STORAGE_GEOHASH_PRECISION = 8;

    private final BusinessRepository businessRepository;
    private final ChangeLogRepository changeLogRepository;
    private final BusinessCache businessCache;

    public BusinessService(
            BusinessRepository businessRepository,
            ChangeLogRepository changeLogRepository,
            BusinessCache businessCache) {
        this.businessRepository = businessRepository;
        this.changeLogRepository = changeLogRepository;
        this.businessCache = businessCache;
    }

    public long create(BusinessCreateRequest request) {
        long id = businessRepository.nextId();
        Instant now = Instant.now();

        Business business = new Business(
                id,
                request.ownerId(),
                request.name(),
                request.category(),
                request.phone(),
                request.address(),
                request.latitude(),
                request.longitude(),
                GeohashUtils.encode(request.latitude(), request.longitude(), STORAGE_GEOHASH_PRECISION),
                BusinessStatus.ACTIVE,
                now,
                now);

        businessRepository.save(business);
        changeLogRepository.append(id, ChangeType.CREATED);
        businessCache.evict(id);
        return id;
    }

    public BusinessResponse update(long id, BusinessUpdateRequest request) {
        Business existing = requireActive(id);
        Instant now = Instant.now();

        Business updated = new Business(
                existing.id(),
                request.ownerId(),
                request.name(),
                request.category(),
                request.phone(),
                request.address(),
                request.latitude(),
                request.longitude(),
                GeohashUtils.encode(request.latitude(), request.longitude(), STORAGE_GEOHASH_PRECISION),
                BusinessStatus.ACTIVE,
                existing.createdAt(),
                now);

        businessRepository.save(updated);
        changeLogRepository.append(id, ChangeType.UPDATED);
        businessCache.evict(id);

        return BusinessResponse.from(updated);
    }

    public void delete(long id) {
        Business existing = requireActive(id);
        Business deleted = new Business(
                existing.id(),
                existing.ownerId(),
                existing.name(),
                existing.category(),
                existing.phone(),
                existing.address(),
                existing.latitude(),
                existing.longitude(),
                existing.geohash(),
                BusinessStatus.DELETED,
                existing.createdAt(),
                Instant.now());

        businessRepository.save(deleted);
        changeLogRepository.append(id, ChangeType.DELETED);
        businessCache.evict(id);
    }

    public BusinessResponse getById(long id) {
        return BusinessResponse.from(requireActive(id));
    }

    public Optional<Business> findActiveById(long id) {
        Optional<Business> cached = businessCache.get(id);
        if (cached.isPresent()) {
            return cached.filter(Business::isActive);
        }

        Optional<Business> fromStore = businessRepository.findById(id).filter(Business::isActive);
        fromStore.ifPresent(businessCache::put);
        return fromStore;
    }

    public List<Business> findAllActiveByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Business> result = new ArrayList<>();
        List<Long> missIds = new ArrayList<>();

        for (Long id : ids) {
            Optional<Business> cached = businessCache.get(id);
            if (cached.isPresent()) {
                Business business = cached.get();
                if (business.status() == BusinessStatus.ACTIVE) {
                    result.add(business);
                }
            } else {
                missIds.add(id);
            }
        }

        if (!missIds.isEmpty()) {
            int batchSize = 1000;
            for (int i = 0; i < missIds.size(); i += batchSize) {
                List<Long> batch = missIds.subList(i, Math.min(i + batchSize, missIds.size()));
                List<Business> fetched = businessRepository.findAllByIds(batch);
                for (Business business : fetched) {
                    if (business.status() == BusinessStatus.ACTIVE) {
                        result.add(business);
                        businessCache.put(business);
                    }
                }
            }
        }

        return result;
    }

    private Business requireActive(long id) {
        return findActiveById(id)
                .orElseThrow(() -> new NotFoundException("business not found: " + id));
    }
}
