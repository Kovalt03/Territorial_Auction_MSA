package com.territorial.item.internal;

import com.territorial.item.domain.item.entity.Item;
import com.territorial.item.domain.item.service.ItemInternalService;
import com.territorial.item.internal.dto.ItemInternalDtos.GrantByIdRequest;
import com.territorial.item.internal.dto.ItemInternalDtos.GrantByTypeRequest;
import com.territorial.item.internal.dto.ItemInternalDtos.GrantResult;
import com.territorial.item.internal.dto.ItemInternalDtos.ItemAdminView;
import com.territorial.item.internal.dto.ItemInternalDtos.UpdatePolicyRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 내부 계약. 네트워크 신뢰 경계(gateway 미노출) — season 보상·admin 위임이 호출한다. */
@RestController
@RequestMapping("/internal/items")
@RequiredArgsConstructor
public class ItemInternalController {

    private final ItemInternalService itemInternalService;

    // season 시즌패스 보상 지급 (아이템 타입 기준)
    @PostMapping("/grants/by-type")
    public ResponseEntity<GrantResult> grantByType(@RequestBody GrantByTypeRequest request) {
        return ResponseEntity.ok(
                itemInternalService.grantByType(
                        request.userId(),
                        Item.ItemType.valueOf(request.itemType()),
                        request.quantity()));
    }

    // admin CS 보상 지급 (아이템 ID 기준)
    @PostMapping("/grants")
    public ResponseEntity<GrantResult> grantById(@RequestBody GrantByIdRequest request) {
        return ResponseEntity.ok(
                itemInternalService.grantById(
                        request.userId(), request.itemId(), request.quantity()));
    }

    // admin 아이템 목록
    @GetMapping
    public ResponseEntity<List<ItemAdminView>> listItems() {
        return ResponseEntity.ok(itemInternalService.listItems());
    }

    // admin 상점 정책 수정
    @PatchMapping("/{itemId}/policy")
    public ResponseEntity<ItemAdminView> updatePolicy(
            @PathVariable Long itemId, @RequestBody UpdatePolicyRequest request) {
        return ResponseEntity.ok(itemInternalService.updatePolicy(itemId, request));
    }
}
