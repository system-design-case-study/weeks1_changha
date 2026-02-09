package com.systemdesigncasestudy.weeks1changha.indexsync.config;

public record HotZoneConfig(
        String geohashPrefix,
        String description,
        int radiusLimit,
        boolean isActive) {
}
