package com.systemdesigncasestudy.weeks1changha.indexsync.domain;

import java.time.Instant;

public record BusinessChangeEvent(
    long id,
    long businessId,
    ChangeType changeType,
    Instant createdAt
) {
}
