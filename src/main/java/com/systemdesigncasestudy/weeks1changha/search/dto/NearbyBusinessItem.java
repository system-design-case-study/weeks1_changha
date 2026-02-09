package com.systemdesigncasestudy.weeks1changha.search.dto;

public record NearbyBusinessItem(
    long id,
    String name,
    String category,
    long distanceM,
    double latitude,
    double longitude
) {
}
