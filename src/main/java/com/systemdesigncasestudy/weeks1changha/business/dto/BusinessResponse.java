package com.systemdesigncasestudy.weeks1changha.business.dto;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import java.time.Instant;

public record BusinessResponse(
    long id,
    long ownerId,
    String name,
    String category,
    String phone,
    String address,
    double latitude,
    double longitude,
    String status,
    Instant updatedAt
) {

    public static BusinessResponse from(Business business) {
        return new BusinessResponse(
            business.id(),
            business.ownerId(),
            business.name(),
            business.category(),
            business.phone(),
            business.address(),
            business.latitude(),
            business.longitude(),
            business.status().name(),
            business.updatedAt()
        );
    }
}
