# 코드 컨벤션

> MSA 전환 완료 — 서비스별 패키지는 `com.territorial.{service}`(예: `com.territorial.user`), 공통 라이브러리는 `com.territorial.auction.global.*`(libs/common). 아래 레이어·네이밍 규칙은 전 서비스 공통이며, 예시의 `com.territorial.auction` 패키지 경로는 규칙 설명용이다.  
> 포맷터: Spotless (Google Java Style 기반)

---

## 1. 패키지 구조

```
com.territorial.auction
├── domain/
│   ├── {domain}/           ← auction, auth, user, map, building, military ...
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   └── ...
└── global/
    ├── common/             ← ApiResponse 등 공통 유틸
    ├── config/             ← Spring 설정 (Security, Redis, JPA 등)
    ├── exception/          ← CustomException, ErrorCode, GlobalExceptionHandler
    ├── security/           ← JWT, OAuth2
    └── validation/         ← 커스텀 Validator 어노테이션
```

**규칙**
- 새 도메인 추가 시 `domain/{domainName}/` 하위에 4개 레이어 폴더 생성
- **서비스 내부** 도메인 간에는 Entity 참조는 허용하되 **다른 도메인 Service 직접 호출은 금지**(이벤트 또는 인터페이스 사용). **서비스 경계를 넘는 데이터**는 `/internal/**` 계약·Kafka/Redis 이벤트로만 접근(다른 서비스 DB·엔티티 직접 참조 금지).
- 횡단 관심사(공통 응답, 예외, 보안)는 반드시 `global/`에 위치

---

## 2. 네이밍 정책

### 클래스명

| 종류 | 규칙 | 예시 |
|---|---|---|
| Controller | `{Domain}Controller` | `AuctionController` |
| Service | `{Domain}Service` | `AuctionService` |
| Repository | `{Domain}Repository` | `AuctionRepository` |
| Entity | 도메인 명사 | `Auction`, `Territory` |
| Request DTO | `{Action}{Domain}Request` — Action이 반드시 앞에 위치 | `PlaceBidRequest`, `UpdateNotificationSettingRequest` |
| Response DTO | `{Action}{Domain}Response` — 조회용 `Get` 접두사는 생략 허용 | `PlaceBidResponse`, `GridMapResponse` |
| 이벤트 | `{Domain}{Action}Event` | `AuctionEndedEvent` |

> **Response `from()` 규칙**: 단일 Entity → Response 변환은 `from(Entity)` 정적 팩토리 필수.  
> 여러 Entity를 조합하는 집계 Response(e.g. `MyProfileResponse`, `GridMapResponse`)는 Service에서 직접 생성 허용.

### 변수명

```java
// boolean: is / has / can 접두사
boolean isInvincible;
boolean hasAttackToken;

// 컬렉션: 복수형
List<Territory> ownedTerritories;
Map<Long, Integer> bidAmountByUserId;

// ID 필드: {entity}Id
Long territoryId;
Long currentBidderId;

// 매직 넘버: 상수 추출
private static final double MIN_BID_INCREASE_RATE = 0.05;
private static final int SIEGE_COUNTDOWN_MINUTES = 30;
```

### 메서드명

| 동사 | 용도 | 예시 |
|---|---|---|
| `find` | 단건 조회 (없으면 예외) | `findActiveAuction()` |
| `get` | 단건 조회 (없으면 null/Optional) | `getMyBid()` |
| `find...List` / `find...All` | 목록 조회 | `findOwnedTerritoryList()` |
| `create` / `register` | 생성 | `createAuction()` |
| `update` / `change` | 수정 | `changeColor()` |
| `delete` / `remove` | 삭제/제거 | `removeDeployedUnit()` |
| `validate` / `check` | 검증 (실패 시 예외) | `validateBidAmount()` |
| `calculate` | 순수 계산 | `calculateTax()` |
| `is` / `has` / `can` | boolean 반환 | `isInvincible()` |

---

## 3. 계층별 책임 규칙

### Controller

```java
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping("/{auctionId}/bid")
    public ResponseEntity<ApiResponse<PlaceBidResponse>> placeBid(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long auctionId,
            @RequestBody @Valid PlaceBidRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.placeBid(userId, auctionId, request)));
    }
}
```

**금지 사항**
- 비즈니스 로직 작성 ❌
- Repository 직접 주입 ❌
- try-catch ❌ (GlobalExceptionHandler가 처리)
- Entity 직접 반환 ❌

### Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 기본 readOnly
public class AuctionService {

    private final AuctionRepository auctionRepository;

    @Transactional  // 쓰기 메서드에만 개별 적용
    public PlaceBidResponse placeBid(Long userId, Long auctionId, PlaceBidRequest request) {
        Auction auction = findActiveAuctionOrThrow(auctionId);
        validateBidAmount(auction, request.bidAmount());
        // ...
        return PlaceBidResponse.from(auction);
    }

    // private 헬퍼 메서드로 의도 표현
    private Auction findActiveAuctionOrThrow(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private void validateBidAmount(Auction auction, int bidAmount) {
        if (bidAmount < auction.getCurrentPrice() * MIN_BID_INCREASE_RATE) {
            throw new CustomException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }
    }
}
```

**규칙**
- 클래스 레벨 `@Transactional(readOnly = true)`, 쓰기 메서드는 `@Transactional` 개별 추가
- Entity → DTO 변환은 Service에서 담당
- `orElseThrow()` 패턴 통일, if-null 패턴 금지
- 메서드 길이 20줄 이하, 초과 시 private 메서드로 분리
- 파라미터 3개 초과 시 Request 객체로 묶기

### Entity

```java
@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용 기본 생성자 보호
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // 연관관계는 항상 LAZY
    @JoinColumn(name = "territory_id", nullable = false)
    private Territory territory;

    @Builder
    public Auction(Territory territory, Integer currentPrice, ...) {
        this.territory = territory;
        this.currentPrice = currentPrice;
        // ...
    }

    // 도메인 행위는 Entity 메서드로 (setter 대신)
    public void extendEndTime(LocalDateTime newEndAt) {
        if (newEndAt.isAfter(this.maxExtendUntil)) {
            throw new CustomException(ErrorCode.AUCTION_MAX_EXTEND_EXCEEDED);
        }
        this.endAt = newEndAt;
    }
}
```

**금지 사항**
- `@Setter` 클래스 레벨 적용 ❌
- `public` 기본 생성자 ❌
- 비즈니스 로직을 Service에만 두고 Entity는 getter/setter bag으로 쓰는 빈혈 모델 ❌
- `FetchType.EAGER` ❌ (N+1 문제)

### Repository

```java
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    // 메서드명으로 의도를 표현
    List<Auction> findByTerritoryIdAndEndAtAfter(Long territoryId, LocalDateTime now);

    // 복잡한 쿼리는 JPQL
    @Query("SELECT a FROM Auction a WHERE a.territory.continent.id = :continentId AND a.endAt > :now")
    List<Auction> findActiveByContinentId(@Param("continentId") Long continentId,
                                          @Param("now") LocalDateTime now);
}
```

**규칙**
- 단순 조회는 JPA 메서드명 방식
- 조인/서브쿼리 포함 복잡한 쿼리는 `@Query` JPQL
- N+1이 우려되는 경우 `@EntityGraph` 또는 Fetch Join 명시

---

## 4. DTO 정책

- **record** 사용 (불변, 보일러플레이트 제거)
- Entity를 Controller 레이어 밖으로 노출 금지
- Response는 정적 팩토리 메서드 `from(Entity)` 패턴

```java
// Request: 검증 어노테이션은 record 필드에 직접
public record PlaceBidRequest(
        @NotNull Long auctionId,
        @Min(1) int bidAmount
) {}

// Response: 정적 팩토리
public record AuctionResponse(
        Long auctionId,
        int currentPrice,
        LocalDateTime endAt
) {
    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getEndAt()
        );
    }
}
```

---

## 5. 예외 처리

기존 구조 (`CustomException` + `ErrorCode` enum) 유지

```java
// 도메인별로 ErrorCode 섹션 구분
// global/exception/ErrorCode.java
// Auction
AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),
BID_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST, "입찰 금액이 현재 최고가보다 낮습니다."),
```

**규칙**
- Service에서 `throw new CustomException(ErrorCode.XXX)` 로 던지기
- Controller에 try-catch 작성 금지 — GlobalExceptionHandler가 처리
- `if (x == null)` 대신 `orElseThrow()` 사용

---

## 6. Clean Code 원칙

### Early Return (중첩 제거)

```java
// ❌
public void attack(Long userId, Long targetId) {
    if (hasToken(userId)) {
        if (!isInvincible(targetId)) {
            if (isPreviousZoneCleared(targetId)) {
                doAttack();
            }
        }
    }
}

// ✅
public void attack(Long userId, Long targetId) {
    validateHasAttackToken(userId);
    validateNotInvincible(targetId);
    validateZoneCleared(targetId);
    doAttack();
}
```

### 한 메서드 = 한 가지 일

```java
// ❌ 여러 책임 혼재
public PlaceBidResponse placeBid(Long userId, PlaceBidRequest req) {
    Auction auction = auctionRepository.findById(req.auctionId())
            .orElseThrow(() -> new CustomException(ErrorCode.AUCTION_NOT_FOUND));
    if (req.bidAmount() < auction.getCurrentPrice() * 1.05) {
        throw new CustomException(ErrorCode.BID_AMOUNT_TOO_LOW);
    }
    wallet.lockAp(req.bidAmount());
    auction.updateBidder(userId, req.bidAmount());
    return PlaceBidResponse.from(auction);
}

// ✅ 역할 분리
public PlaceBidResponse placeBid(Long userId, PlaceBidRequest req) {
    Auction auction = findActiveAuctionOrThrow(req.auctionId());
    validateBidAmount(auction, req.bidAmount());
    lockAp(userId, req.bidAmount());
    auction.updateBidder(userId, req.bidAmount());
    return PlaceBidResponse.from(auction);
}
```

---

## 7. 주석 정책

- **원칙: 코드로 의도를 표현. 주석 최소화.**
- 아래 경우에만 허용:

```java
// ✅ 비즈니스 규칙의 "왜"를 설명
// Anti-Sniping: 종료 1분 전 입찰 시 30초 연장 (최대 10분) — 기획서 §4.3
if (isWithinSnipingWindow(auction)) {
    extendAuction(auction);
}

// ✅ 복잡한 계산식
// ATK = Σ(파견 유닛 attack_power × 수량), DEF = Σ(배치 유닛 defense_power) + Σ(Zone 방어 건물)
int attackPower = calculateAttackPower(deployedUnits);

// ❌ 코드가 이미 말하고 있는 것 반복
// 유저 ID로 경매를 찾는다
Auction auction = findActiveAuctionOrThrow(userId);
```

---

## 8. 공통 응답 형식

기존 `ApiResponse<T>` 유지

```java
// 성공
return ResponseEntity.ok(ApiResponse.ok(response));
return ResponseEntity.status(201).body(ApiResponse.created(response));

// 데이터 없는 성공 (204 대신 200 + null 사용)
return ResponseEntity.ok(ApiResponse.ok(null));
```
