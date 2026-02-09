package com.systemdesigncasestudy.weeks1changha.indexsync.service;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.repository.BusinessRepository;
import com.systemdesigncasestudy.weeks1changha.cache.GeoCellCache;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.BusinessChangeEvent;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.ChangeLogRepository;
import com.systemdesigncasestudy.weeks1changha.indexsync.repository.GeohashIndexRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IndexSyncService {

    private final ChangeLogRepository changeLogRepository;
    private final BusinessRepository businessRepository;
    private final GeohashIndexRepository geohashIndexRepository;
    private final GeoCellCache geoCellCache;
    private final int batchSize;
    private final Timer syncLatencyTimer;
    private final Counter processedEventCounter;

    public IndexSyncService(
        ChangeLogRepository changeLogRepository,
        BusinessRepository businessRepository,
        GeohashIndexRepository geohashIndexRepository,
        GeoCellCache geoCellCache,
        MeterRegistry meterRegistry,
        @Value("${app.index-sync.batch-size:500}") int batchSize
    ) {
        this.changeLogRepository = changeLogRepository;
        this.businessRepository = businessRepository;
        this.geohashIndexRepository = geohashIndexRepository;
        this.geoCellCache = geoCellCache;
        this.batchSize = batchSize;
        this.syncLatencyTimer = Timer.builder("proximity.indexsync.latency")
            .description("Latency for one index-sync batch run")
            .publishPercentileHistogram()
            .register(meterRegistry);
        this.processedEventCounter = Counter.builder("proximity.indexsync.processed.events")
            .description("Number of processed change events")
            .register(meterRegistry);
        Gauge.builder("proximity.indexsync.backlog", changeLogRepository, ChangeLogRepository::countUnprocessed)
            .description("Number of unprocessed change events")
            .register(meterRegistry);
        Gauge.builder("proximity.indexsync.oldest.age.seconds", this::oldestUnprocessedAgeSeconds)
            .description("Age in seconds of the oldest unprocessed change event")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.index-sync.delay-ms:30000}")
    public void scheduledSync() {
        syncOnce(batchSize);
    }

    public int syncOnce(int maxEvents) {
        Timer.Sample sample = Timer.start();
        try {
            List<BusinessChangeEvent> events = changeLogRepository.pollUnprocessed(maxEvents);
            if (events.isEmpty()) {
                return 0;
            }

            List<Long> processedIds = new ArrayList<>();
            for (BusinessChangeEvent event : events) {
                applyEvent(event);
                processedIds.add(event.id());
            }

            changeLogRepository.markProcessed(processedIds);
            geoCellCache.clear();
            processedEventCounter.increment(processedIds.size());
            return processedIds.size();
        } finally {
            sample.stop(syncLatencyTimer);
        }
    }

    private double oldestUnprocessedAgeSeconds() {
        Instant oldest = changeLogRepository.oldestUnprocessedCreatedAt().orElse(null);
        if (oldest == null) {
            return 0d;
        }
        return Duration.between(oldest, Instant.now()).toSeconds();
    }

    private void applyEvent(BusinessChangeEvent event) {
        if (event.changeType() == ChangeType.DELETED) {
            geohashIndexRepository.deleteByBusinessId(event.businessId());
            return;
        }

        Business business = businessRepository.findById(event.businessId()).orElse(null);
        if (business == null || !business.isActive()) {
            geohashIndexRepository.deleteByBusinessId(event.businessId());
            return;
        }

        geohashIndexRepository.upsert(business.geohash(), business.id());
    }
}
