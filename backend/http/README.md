# API 테스트 가이드

## Postman

### 준비
1. [Postman](https://www.postman.com/downloads/) 설치
2. `Territorial-Auction.postman_collection.json` import
   - Postman 좌측 상단 `Import` 클릭 → 파일 선택
3. Environment 설정
   - 좌측 `Environments` → `+` → 이름: `local`
   - 변수 추가:

     | Variable | Current Value |
     |----------|--------------|
     | baseUrl | http://localhost:8080 |
     | accessToken | (비워두기) |

   - 우측 상단 드롭다운에서 `local` 선택

### 테스트 순서
1. 회원가입
2. 로그인 → `accessToken` 자동 저장됨
3. 중복 확인 (username / email / nickname)
4. AccessToken 갱신 → `accessToken` 자동 갱신됨
5. 로그아웃 → `accessToken` 자동 초기화됨

### 새 API 추가 시
1. Postman에서 요청 추가 및 테스트 확인
2. Collection export (`...` → `Export`) → 기존 JSON 파일 덮어쓰기
3. git commit

---

## HTTP Client (IntelliJ Ultimate 전용)

`auth.http` 파일을 IntelliJ Ultimate에서 열면 각 요청 옆 ▶ 버튼으로 바로 실행 가능.

### 준비
- IntelliJ Ultimate 버전 필요 (Community 버전 미지원)
- `http-client.env.json`에서 환경 설정 관리
- `.http` 파일 상단에서 환경 선택 후 실행

### Community 버전이라면
`auth.http` 파일 상단의 변수 선언 방식으로 URL을 직접 설정할 수 있으나,
실행은 불가능하므로 Postman 사용 권장.
