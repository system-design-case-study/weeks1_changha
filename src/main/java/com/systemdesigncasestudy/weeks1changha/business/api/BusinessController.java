package com.systemdesigncasestudy.weeks1changha.business.api;

import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessCreateRequest;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessCreatedResponse;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessResponse;
import com.systemdesigncasestudy.weeks1changha.business.dto.BusinessUpdateRequest;
import com.systemdesigncasestudy.weeks1changha.business.service.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable("id") @Positive long id) {
        return businessService.getById(id);
    }

    @PostMapping
    public ResponseEntity<BusinessCreatedResponse> create(@RequestBody @Valid BusinessCreateRequest request) {
        long id = businessService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BusinessCreatedResponse(id));
    }

    @PutMapping("/{id}")
    public BusinessResponse update(
        @PathVariable("id") @Positive long id,
        @RequestBody @Valid BusinessUpdateRequest request
    ) {
        return businessService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") @Positive long id) {
        businessService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
