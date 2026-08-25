package com.territorial.auction.domain.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.territorial.auction.domain.map.dto.GridMapResponse;
import com.territorial.auction.domain.map.service.MapGridEtagService;
import com.territorial.auction.domain.map.service.MapService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MapControllerTest {

    @InjectMocks private MapController mapController;

    @Mock private MapService mapService;
    @Mock private MapGridEtagService mapGridEtagService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mapController).build();
    }

    @Test
    @DisplayName("같은 ETag 요청은 본문 없이 304를 반환한다")
    void getGridMap_matchingEtag_returnsNotModified() throws Exception {
        given(mapGridEtagService.current()).willReturn("\"grid-version\"");
        given(mapService.getGridMap(null)).willReturn(response());

        MvcResult first =
                mockMvc.perform(get("/api/v1/map/grid"))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.ETAG, notNullValue()))
                        .andExpect(
                                header().string(
                                                HttpHeaders.CACHE_CONTROL,
                                                containsString("no-cache")))
                        .andReturn();

        String eTag = first.getResponse().getHeader(HttpHeaders.ETAG);
        MvcResult conditional =
                mockMvc.perform(get("/api/v1/map/grid").header(HttpHeaders.IF_NONE_MATCH, eTag))
                        .andExpect(status().isNotModified())
                        .andExpect(content().string(""))
                        .andReturn();

        assertThat(conditional.getResponse().getHeaders(HttpHeaders.ETAG)).containsExactly(eTag);
        verify(mapService, times(1)).getGridMap(null);
    }

    private GridMapResponse response() {
        return new GridMapResponse(
                50,
                List.of(
                        new GridMapResponse.GridTerritoryDto(
                                1L, 2, 3, 4L, "owner", "#00f5ff", "A", "OCCUPIED", false, 5L, 10)));
    }
}
