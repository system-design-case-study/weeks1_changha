package com.systemdesigncasestudy.weeks1changha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemdesigncasestudy.weeks1changha.indexsync.service.IndexSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProximityServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IndexSyncService indexSyncService;

    @Test
    void chapter1MinimumFlowWorks() throws Exception {
        String createRequest = """
            {
              "ownerId": 12,
              "name": "Cafe Alpha",
              "category": "CAFE",
              "phone": "02-1234-5678",
              "address": "Seoul",
              "latitude": 37.4991,
              "longitude": 127.0313
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

        JsonNode createdBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long businessId = createdBody.get("id").asLong();

        mockMvc.perform(get("/v1/search/nearby")
                .param("latitude", "37.4991")
                .param("longitude", "127.0313")
                .param("radius", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));

        indexSyncService.syncOnce(100);

        mockMvc.perform(get("/v1/search/nearby")
                .param("latitude", "37.4991")
                .param("longitude", "127.0313")
                .param("radius", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.businesses[0].id").value(businessId));

        mockMvc.perform(get("/v1/business/{id}", businessId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cafe Alpha"));

        String updateRequest = """
            {
              "ownerId": 12,
              "name": "Cafe Alpha 2",
              "category": "CAFE",
              "phone": "02-1234-5678",
              "address": "Seoul",
              "latitude": 37.4991,
              "longitude": 127.0313
            }
            """;

        mockMvc.perform(put("/v1/business/{id}", businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cafe Alpha 2"));

        mockMvc.perform(delete("/v1/business/{id}", businessId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/business/{id}", businessId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        indexSyncService.syncOnce(100);

        mockMvc.perform(get("/v1/search/nearby")
                .param("latitude", "37.4991")
                .param("longitude", "127.0313")
                .param("radius", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void searchValidationWorks() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                .param("latitude", "95")
                .param("longitude", "127.0313")
                .param("radius", "500"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
