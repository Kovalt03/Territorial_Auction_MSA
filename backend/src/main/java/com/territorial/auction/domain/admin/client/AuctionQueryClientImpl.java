package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuctionQueryClientImpl implements AuctionQueryClient {

    private final RestClient restClient;

    public AuctionQueryClientImpl(
            RestClient.Builder builder, @Value("${auction-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public long countActiveAuctions() {
        Long count =
                restClient.get().uri("/internal/auctions/active-count").retrieve().body(Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public AdminUserBidListResponse getBids(Long bidderId, Pageable pageable) {
        return restClient
                .get()
                .uri(
                        "/internal/auctions/bidders/{id}/bids?page={page}&size={size}",
                        bidderId,
                        pageable.getPageNumber(),
                        pageable.getPageSize())
                .retrieve()
                .body(AdminUserBidListResponse.class);
    }

    @Override
    public AdminUserActiveBidListResponse getActiveBids(Long bidderId) {
        return restClient
                .get()
                .uri("/internal/auctions/bidders/{id}/active-bids", bidderId)
                .retrieve()
                .body(AdminUserActiveBidListResponse.class);
    }
}
