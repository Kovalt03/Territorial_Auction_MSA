# 부하·병목 테스트 계획 및 병목 리스트

돌릴 시나리오·상태와, 측정으로 확정/반증된 병목을 추적하는 문서. 방법론·SLO·판독 기준은 [performance-testing.md](./performance-testing.md).
회차별 상세 측정 수치는 로컬 `report/load/YYYY-MM-DD-*.md`(gitignored)에서 재현·기록한다 — 이 문서에는 판단에 필요한 핵심 근거만 요약한다.

상태: ✅ 완료 · 🔄 진행 중 · ⬜ 예정 · ⛔ 블록(선행 필요)

## 실행 준비 (매 캠페인 공통)
1. 서브셋 기동: `docker compose -f docker-compose.msa.yml -f docker-compose.loadtest.yml --profile <path> up -d --build`
2. 리소스 독립성 확인: `./scripts/loadtest-verify-isolation.sh <path>`
3. 시드: `./scripts/loadtest-seed.sh 200`
4. **토큰: 인증 필요 시나리오는 덤프와 실행을 한 명령으로 붙인다** — JWT access TTL이 1h라 덤프-실행 사이 시간이 벌어지면 게이트웨이 401 → 전 요청 KO(무효 회차)가 된다:
   `./scripts/dump-tokens.sh 200 && (cd loadtest && ./gradlew gatlingRun --simulation …AuctionBidSimulation -D…)`
   (map 조회 시나리오는 공개 경로라 토큰 불필요)
5. 부하: `cd loadtest && JAVA_HOME=… ./gradlew gatlingRun --simulation … -D…` (Docker 밖 네이티브)
6. 회차 수치를 `report/load/`에 기록

## 시나리오 백로그
### 우선순위 1 — 경매 입찰 (auction-path)
| ID | 시나리오 | 목적 | 파라미터 | 상태 |
|----|---------|------|---------|------|
| 1a | 분산 입찰 100 VU (Load) | 게이트웨이 end-to-end 베이스라인 | `-Dusers=100 -Dramp=30 -Dhold=180` | ✅ |
| 1b | auction 캡 1.0→2.0 프로브 | CPU가 병목인지 검증 | override cpus 2.0 | ✅ |
| 1c | 동일 경매 1건 경합 | 분산락 직렬화 비용 | `-DauctionMin=4 -DauctionMax=4` | ✅ |
| 1d | Stress 300 VU | 용량 천장·먼저 무너지는 서비스 | `-Dusers=300 -Dramp=60 -Dhold=120` | ✅ |
| 1h | 200 VU (knee 정밀화) | 100→300 붕괴 지점 특정 | `-Dusers=200 -Dramp=45 -Dhold=120` | 🔄 |
| 1e | 에스크로 홉 계측·정량화 | auction→user 왕복 지연 | (계측 이미 존재 — 트래픽 후 http.client.requests 노출) | ✅ |
| 1f | Spike (순간 10배) | 급증 후 회복 | 20→200 급상승 | ⬜ |
| 1g | Soak 1시간 | 메모리·커넥션·lag 누수 | 중간 부하 60m+ | ⬜ |

### 우선순위 2 — 맵 조회 (map-path)
| ID | 시나리오 | 목적 | 상태 |
|----|---------|------|------|
| 2a-grid | `GET /map/grid` 100VU | 최대 트래픽·직렬화 | ✅ |
| 2a-detail | `GET /map/territories/{id}` | 상세(combat storage 합성) | ✅ combat 기동 시 200(combat 부재 시 500=B4) |
| 2b | '경매중' 뱃지 read-model 격리 | 핫패스 격리 검증 | ✅ grid는 로컬 territory_auction_status 프로젝션만 읽음, auction 미호출(코드 확인) |

### 우선순위 3·4
| ID | 시나리오 | 상태 |
|----|---------|------|
| 3a-read | combat 조회 부하 (금고·유닛) 100VU | ✅ (combat이 user.created로 섬·금고 자동 프로비저닝) |
| 3a-write | 유닛 생산/연구/금고 이전 — 금고 락 경합 | ⛔ bootstrap이 섬+성+빈금고만 줌 → 병영·연구소·구매가능종류 시딩 필요(combat loadtest 시더) |
| 4a-conn | realtime 기동 + WS 핸드셰이크(`/ws/info`) | ✅ |
| 4a-load | 100 구독 + 이벤트 1건 → WS 반영 지연 | ⛔ STOMP-over-SockJS 전용 WS 클라이언트 필요(Gatling 난도↑, 문서 §5.4 권고대로 별도) |

### 회복력 / 교차
| ID | 시나리오 | 상태 |
|----|---------|------|
| R1 | 의도적 OOM → 복구 관측 | ✅ restart 정책 부여 후 mem 축소로 OOM 유발→auto-restart 복구 확인(B5) |
| X1 | 스케줄러(생산·토지세·시즈)와 부하 동시 실행 | ⬜ |

## 병목 리스트 (해결은 하나씩)
근거 수치는 auction-path 서브셋, 게이트웨이(:8090) 경유, cgroup 상한(auction 1.0·gateway 0.75코어 등) 하에서 측정.

### 반증됨 — 건드리지 않는다
- **분산락(auction:lock)**: 동일경매 100VU ≈ 분산 100VU 처리량(약 728 vs 693 rps) → 락은 처리량 병목 아님.
- **DB 커넥션 풀**: hikari.pending 전 구간 0.
- **auction CPU 단독**: 캡 1.0→2.0 상향해도 100VU 처리량 무변화 → CPU 여유가 아니라 지연이 한계.

### 실제 이슈 — 고칠 것
| # | 병목 | 근거 | 해결 방향 | 선행 |
|---|------|------|----------|------|
| **B1** | 성공 입찰 지연을 auction→user 에스크로 동기 홉이 지배 (CPU로 안 줄음) | 1b(CPU 2배에도 p95 불변) + 1e: `http.client.requests` uri=`/internal/wallets/bid-escrow` 홉 평균 28~110ms(부하 의존). 성공 입찰 경로의 주 비용 | 에스크로 비동기화/배치, 또는 홉 자체(user DB 커밋) 단축 | — (계측 확보됨) |
| ~~**B2**~~ 🔶 부분 수정(#66) | 과부하 시 처리량 붕괴 — 100VU 대비 300VU에서 오히려 처리량↓·p95 SLO 초과 | 1d(300VU 붕괴), gateway·auction CPU 동시 포화 | **인터서비스 타임아웃(connect 2s/read 3s) 적용 → 다운스트림 정지 시 무한대기 대신 상한 실패(검증: user pause 시 3.18s)**. 처리량 절대치는 1코어캡(로컬 아티팩트). 동시성 상한/부하차단(admission control)은 추가 여지 | — |
| ~~**B3**~~ ✅ 수정(#65) | **`GET /map/grid` 동시성 하 붕괴** — 100VU서 p95 7s·22rps(읽기 SLO 250ms의 28×) | 2a-grid. solo 29ms인데 동시엔 초 단위. 전 2500영토 조립+직렬화가 CPU무거움×1코어캡. 닉네임은 배치라 N+1 아님 | **직렬화 JSON 로컬 캐시(etag 키) 적용 → 317rps·p95 498ms(14×)**. 잔여는 1코어캡 물리한계(페이지네이션은 추가 여지) | — |
| ~~**B4**~~ ✅ 수정(#65) | **territory-detail이 combat 다운 시 폴백 없이 500** | 2a-detail. `/internal/combat/.../storage` 동기 호출 실패가 그대로 500 | **읽기 getTerritoryStorage만 RestClientException 잡아 빈 저장소로 degrade → 500 대신 200**(검증됨) | — |
| ~~**B5**~~ ✅ 수정 | **restart 정책 부재 → OOM 시 서비스가 죽은 채 유지(자가복구 없음)** | R1. base compose restart=no. 정책 부여 후엔 OOM→auto-restart 복구 확인 | **docker-compose.msa.yml 전 서비스(25)에 `restart: unless-stopped` 추가** | — |

### 테스트-환경 아티팩트 (로컬 캡 산물 — 코드 이슈 아님, prod는 수평 확장)
- gateway CPU가 stress에서 0.75코어 캡 포화, auction CPU 1코어 포화. 용량 산정 참고치일 뿐, 코드 수정 대상 아님.

### 확정 전 선행 작업
- **1e**: WalletClient를 auto-config RestClient/WebClient로 → `http.client.requests` 노출 → B1 정량화.
- **R1**: base compose에 `restart: unless-stopped` → 의도적 OOM 복구 관측 가능.
