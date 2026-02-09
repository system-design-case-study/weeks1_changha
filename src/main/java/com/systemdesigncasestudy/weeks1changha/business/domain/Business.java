package com.systemdesigncasestudy.weeks1changha.business.domain;

import java.time.Instant;

public record Business(
    long id,
    long ownerId,
    String name,
    String category,
    String phone,
    String address,
    double latitude,
    double longitude,
    String geohash,
    BusinessStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public boolean isActive() {
        return status == BusinessStatus.ACTIVE;
    }
}
