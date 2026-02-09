package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import com.systemdesigncasestudy.weeks1changha.indexsync.domain.BusinessChangeEvent;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChangeLogRepository {

    void append(long businessId, ChangeType changeType);

    List<BusinessChangeEvent> pollUnprocessed(int limit);

    void markProcessed(Collection<Long> eventIds);

    int countUnprocessed();

    Optional<Instant> oldestUnprocessedCreatedAt();
}
