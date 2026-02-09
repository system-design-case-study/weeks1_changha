package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import com.systemdesigncasestudy.weeks1changha.indexsync.domain.BusinessChangeEvent;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("!mysql")
@Repository
public class InMemoryChangeLogRepository implements ChangeLogRepository {

    private final AtomicLong sequence = new AtomicLong(1L);
    private final List<BusinessChangeEvent> events = new ArrayList<>();
    private final Set<Long> processedIds = new HashSet<>();

    @Override
    public synchronized void append(long businessId, ChangeType changeType) {
        long eventId = sequence.getAndIncrement();
        events.add(new BusinessChangeEvent(eventId, businessId, changeType, Instant.now()));
    }

    @Override
    public synchronized List<BusinessChangeEvent> pollUnprocessed(int limit) {
        List<BusinessChangeEvent> result = new ArrayList<>();
        for (BusinessChangeEvent event : events) {
            if (processedIds.contains(event.id())) {
                continue;
            }
            result.add(event);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public synchronized void markProcessed(Collection<Long> eventIds) {
        processedIds.addAll(eventIds);
    }

    @Override
    public synchronized int countUnprocessed() {
        int count = 0;
        for (BusinessChangeEvent event : events) {
            if (!processedIds.contains(event.id())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized Optional<Instant> oldestUnprocessedCreatedAt() {
        Instant oldest = null;
        for (BusinessChangeEvent event : events) {
            if (processedIds.contains(event.id())) {
                continue;
            }
            if (oldest == null || event.createdAt().isBefore(oldest)) {
                oldest = event.createdAt();
            }
        }
        return Optional.ofNullable(oldest);
    }
}
