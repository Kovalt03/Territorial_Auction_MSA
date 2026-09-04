package com.territorial.auction.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.territorial.auction.domain.user.dto.WishlistResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wishlist;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WishlistRepository;
import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @InjectMocks private WishlistService wishlistService;

    @Mock private WishlistRepository wishlistRepository;
    @Mock private MapTerritoryClient mapTerritoryClient;
    @Mock private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
    }

    private Wishlist sampleWishlist(Long territoryId) {
        Wishlist wishlist = Wishlist.builder().user(user).territoryId(territoryId).build();
        ReflectionTestUtils.setField(wishlist, "id", territoryId);
        return wishlist;
    }

    // ─── getWishlist() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWishlist()")
    class GetWishlist {

        @Test
        @DisplayName("항목 있을 때 → territoryIds 크기·내용 일치")
        void getWishlist_success() {
            Wishlist w1 = sampleWishlist(10L);
            Wishlist w2 = sampleWishlist(20L);
            given(wishlistRepository.findAllByUserId(1L)).willReturn(List.of(w1, w2));

            WishlistResponse response = wishlistService.getWishlist(1L);

            assertThat(response.territoryIds()).hasSize(2);
            assertThat(response.territoryIds()).containsExactly(10L, 20L);
        }

        @Test
        @DisplayName("빈 위시리스트 → territoryIds = [] (null 아님)")
        void getWishlist_empty() {
            given(wishlistRepository.findAllByUserId(1L)).willReturn(List.of());

            WishlistResponse response = wishlistService.getWishlist(1L);

            assertThat(response.territoryIds()).isNotNull();
            assertThat(response.territoryIds()).isEmpty();
        }
    }

    // ─── addWishlist() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addWishlist()")
    class AddWishlist {

        @Test
        @DisplayName("정상 추가 → save() 1회 호출")
        void addWishlist_success() {
            given(mapTerritoryClient.exists(5L)).willReturn(true);
            given(wishlistRepository.existsByUserIdAndTerritoryId(1L, 5L)).willReturn(false);

            wishlistService.addWishlist(1L, 5L);

            then(wishlistRepository).should().save(any(Wishlist.class));
        }

        @Test
        @DisplayName("영토 미존재 → TERRITORY_NOT_FOUND")
        void addWishlist_territoryNotFound() {
            given(mapTerritoryClient.exists(999L)).willReturn(false);

            assertThatThrownBy(() -> wishlistService.addWishlist(1L, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }

        @Test
        @DisplayName("중복 추가 → WISHLIST_ALREADY_EXISTS")
        void addWishlist_alreadyExists() {
            given(mapTerritoryClient.exists(5L)).willReturn(true);
            given(wishlistRepository.existsByUserIdAndTerritoryId(1L, 5L)).willReturn(true);

            assertThatThrownBy(() -> wishlistService.addWishlist(1L, 5L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }
    }

    // ─── removeWishlist() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeWishlist()")
    class RemoveWishlist {

        @Test
        @DisplayName("정상 제거 → delete() 1회 호출")
        void removeWishlist_success() {
            Wishlist wishlist = sampleWishlist(5L);
            given(wishlistRepository.findByUserIdAndTerritoryId(1L, 5L))
                    .willReturn(Optional.of(wishlist));

            wishlistService.removeWishlist(1L, 5L);

            then(wishlistRepository).should().delete(wishlist);
        }

        @Test
        @DisplayName("미등록 영토 제거 → WISHLIST_NOT_FOUND")
        void removeWishlist_notFound() {
            given(wishlistRepository.findByUserIdAndTerritoryId(1L, 5L))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistService.removeWishlist(1L, 5L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.WISHLIST_NOT_FOUND);
        }
    }
}
