package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class MapGridEtagServiceTest {

    @InjectMocks private MapGridEtagService mapGridEtagService;

    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;
    @Mock private Cache.ValueWrapper cachedValue;

    @Test
    @DisplayName("캐시된 그리드 버전을 ETag로 반환한다")
    void current_cachedVersion_returnsCachedEtag() {
        given(cacheManager.getCache("territory-grid-etag")).willReturn(cache);
        given(cache.get("version")).willReturn(cachedValue);
        given(cachedValue.get()).willReturn("\"grid-version\"");

        assertThat(mapGridEtagService.current()).isEqualTo("\"grid-version\"");
    }

    @Test
    @DisplayName("그리드 버전이 없으면 새 ETag를 원자적으로 저장한다")
    void current_missingVersion_generatesAndStoresEtag() {
        given(cacheManager.getCache("territory-grid-etag")).willReturn(cache);

        String eTag = mapGridEtagService.current();

        assertThat(eTag).startsWith("\"").endsWith("\"");
        verify(cache).putIfAbsent("version", eTag);
    }
}
