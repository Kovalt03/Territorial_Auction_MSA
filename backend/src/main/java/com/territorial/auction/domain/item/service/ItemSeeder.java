package com.territorial.auction.domain.item.service;

import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.Item.ItemType;
import com.territorial.auction.domain.item.repository.ItemRepository;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class ItemSeeder implements ApplicationRunner {

    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (itemRepository.count() > 0) {
            log.info("items 이미 존재 — 건너뜀");
            return;
        }
        List<Item> items = loadEntries().stream().map(this::toEntity).toList();
        itemRepository.saveAll(items);
        log.info("items 시드 완료. 건수={}", items.size());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntries() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/items.yml")) {
            Map<String, Object> root = yaml.load(is);
            return (List<Map<String, Object>>) root.get("items");
        } catch (Exception e) {
            throw new IllegalStateException("items.yml 로드 실패", e);
        }
    }

    private Item toEntity(Map<String, Object> m) {
        return Item.builder()
                .name((String) m.get("name"))
                .itemType(ItemType.valueOf((String) m.get("itemType")))
                .description((String) m.get("description"))
                .costAp((Integer) m.get("costAp"))
                .costGp((Integer) m.get("costGp"))
                .dailyLimit((Integer) m.get("dailyLimit"))
                .gpReward((Integer) m.get("gpReward"))
                .iconUrl((String) m.get("iconUrl"))
                .build();
    }
}
