package com.systemdesigncasestudy.weeks1changha.search.api;

import com.systemdesigncasestudy.weeks1changha.search.dto.NearbySearchResponse;
import com.systemdesigncasestudy.weeks1changha.search.service.SearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/nearby")
    public NearbySearchResponse searchNearby(
        @RequestParam("latitude") @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @RequestParam("longitude") @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @RequestParam(value = "radius", defaultValue = "5000") @Min(100) @Max(50000) int radius,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return searchService.searchNearby(latitude, longitude, radius, limit, cursor);
    }
}
