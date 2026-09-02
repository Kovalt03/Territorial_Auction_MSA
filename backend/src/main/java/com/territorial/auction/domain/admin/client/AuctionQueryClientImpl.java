package com.territorial.auction.domain.admin.client;

import com.territorial.auction.domain.admin.dto.AdminAuctionListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.auction.domain.admin.dto.AdminUserBidListResponse;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuctionQueryClientImpl implements AuctionQueryClient {

    private final RestClient restClient;

    public AuctionQueryClientImpl(
            RestClient.Builder builder,
            @Value("${auction-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
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

    @Override
    public AdminAuctionListResponse getActiveAuctions(Pageable pageable) {
        return restClient
                .get()
                .uri(
                        "/internal/auctions/active?page={page}&size={size}",
                        pageable.getPageNumber(),
                        pageable.getPageSize())
                .retrieve()
                .body(AdminAuctionListResponse.class);
    }

    @Override
    public void forceSettle(Long auctionId) {
        restClient
                .post()
                .uri("/internal/auctions/{id}/force-settle", auctionId)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
                        })
                .onStatus(
                        status -> status.value() == 409,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.AUCTION_NO_BIDDER_TO_SETTLE);
                        })
                .toBodilessEntity();
    }

    @Override
    public void forceCancel(Long auctionId) {
        restClient
                .post()
                .uri("/internal/auctions/{id}/force-cancel", auctionId)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
                        })
                .onStatus(
                        status -> status.value() == 409,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.AUCTION_ALREADY_SETTLED);
                        })
                .toBodilessEntity();
    }
}
