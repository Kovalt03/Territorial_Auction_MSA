package com.territorial.map.domain.map.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MapGridEtagService {

    private static final String CACHE_NAME = "territory-grid-etag";
    private static final String VERSION_KEY = "version";

    private final CacheManager cacheManager;

    public String current() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Map grid ETag cache is unavailable");
        }

        Cache.ValueWrapper cached = cache.get(VERSION_KEY);
        if (cached != null) {
            return (String) cached.get();
        }

        String generated = "\"" + UUID.randomUUID() + "\"";
        Cache.ValueWrapper concurrentValue = cache.putIfAbsent(VERSION_KEY, generated);
        return concurrentValue == null ? generated : (String) concurrentValue.get();
    }
}
