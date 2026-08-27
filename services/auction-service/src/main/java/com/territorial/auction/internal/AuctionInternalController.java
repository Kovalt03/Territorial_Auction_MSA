package com.territorial.auction.internal;

import com.territorial.auction.internal.dto.AdminActiveBidListView;
import com.territorial.auction.internal.dto.AdminBidPageView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AuctionInternalController(AdminAuctionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/active-count")
    public long countActiveAuctions() {
        return queryService.countActiveAuctions();
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
