package com.territorial.auction.internal;

import com.territorial.auction.internal.dto.AdminActiveBidListView;
import com.territorial.auction.internal.dto.AdminAuctionListView;
import com.territorial.auction.internal.dto.AdminBidPageView;
import com.territorial.auction.service.AuctionLifecycleService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모놀리식 admin이 호출하는 내부 질의 엔드포인트. 게이트웨이를 거치지 않고 서비스 간 직접 호출되며, ApiResponse 래핑 없이 원시 DTO를 반환한다(모놀리식
 * /internal 규약과 동일).
 */
@RestController
@RequestMapping("/internal/auctions")
public class AuctionInternalController {

    private final AdminAuctionQueryService queryService;
    private final AuctionLifecycleService lifecycleService;

    public AuctionInternalController(
            AdminAuctionQueryService queryService, AuctionLifecycleService lifecycleService) {
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/active-count")
    public long countActiveAuctions() {
        return queryService.countActiveAuctions();
    }

    @GetMapping("/active")
    public AdminAuctionListView getActiveAuctions(
            @PageableDefault(size = 20, sort = "endAt", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return queryService.getActiveAuctions(pageable);
    }

    @PostMapping("/{auctionId}/force-settle")
    public void forceSettle(@PathVariable Long auctionId) {
        lifecycleService.forceSettle(auctionId);
    }

    @PostMapping("/{auctionId}/force-cancel")
    public void forceCancel(@PathVariable Long auctionId) {
        lifecycleService.forceCancel(auctionId);
    }

    @GetMapping("/bidders/{bidderId}/bids")
    public AdminBidPageView getBids(
            @PathVariable Long bidderId, @PageableDefault(size = 20) Pageable pageable) {
        return queryService.getBids(bidderId, pageable);
    }

    @GetMapping("/bidders/{bidderId}/active-bids")
    public AdminActiveBidListView getActiveBids(@PathVariable Long bidderId) {
        return queryService.getActiveBids(bidderId);
    }
}
