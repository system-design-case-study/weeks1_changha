package com.systemdesigncasestudy.weeks1changha.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record NearbySearchResponse(
    int total,
    @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
    List<NearbyBusinessItem> businesses
) {
}
