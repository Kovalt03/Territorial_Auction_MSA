# Auth API

> Notion 원본: https://www.notion.so/API-3332efa4278d81ca8919eef1a837288c  
> 구현 상태: ✅ 완료

---

## 회원가입

**POST** `/api/v1/auth/signup`

### Request

```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password1!",
  "nickname": "테스트유저"
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `username` | String | Y | 영문+숫자, 4~20자 |
| `email` | String | Y | 이메일 형식 |
| `password` | String | Y | 영문+숫자+특수문자 포함, 8자 이상 |
| `nickname` | String | Y | 2~20자 |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "userId": 1,
    "username": "testuser",
    "nickname": "테스트유저"
  }
}
```

### 부가 효과
가입 성공 시 user-service가 `wallets`, `notification_settings`와 `user.created` outbox 이벤트를 만든다. 이벤트를 받은 모놀리식은 `user_profiles` 읽기 프로젝션을, combat-service는 `home_islands`와 기본 성을 생성한다.

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 409 | DUPLICATE_USERNAME | 유저네임 중복 |
| 409 | DUPLICATE_EMAIL | 이메일 중복 |
| 409 | DUPLICATE_NICKNAME | 닉네임 중복 |
| 400 | INVALID_INPUT | 유효성 검증 실패 |

---

## 로그인

**POST** `/api/v1/auth/login`

### Request

```json
{
  "email": "test@example.com",
  "password": "password1!"
}
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGci..."
  }
}
```

**Set-Cookie**: `refreshToken=...; HttpOnly; Secure; Path=/api/v1/auth; Max-Age=1209600`

### Postman 스크립트
```javascript
const token = pm.response.json().data.accessToken;
pm.environment.set('accessToken', token);
```

### 에러

| HTTP | 에러 코드 | 설명 |
|-----|---------------------|----------------|
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 |
| 403 | WITHDRAWN_USER | 탈퇴한 계정 |
| 403 | SUSPENDED_USER | 정지된 계정 |
| 404 | USER_NOT_FOUND | 존재하지 않는 이메일(계정) |

---

## AccessToken 갱신

**POST** `/api/v1/auth/refresh`

- Cookie의 `refreshToken`으로 새 AccessToken 발급
- 인증 헤더 불필요

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGci..."
  }
}
```

**Set-Cookie**: refreshToken 재발급

### Postman 스크립트
```javascript
const token = pm.response.json().data.accessToken;
pm.environment.set('accessToken', token);
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | INVALID_REFRESH_TOKEN | 리프레시 토큰 없음 또는 만료 |

---

## 로그아웃

**POST** `/api/v1/auth/logout`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

**Set-Cookie**: `refreshToken=; HttpOnly; Secure; Path=/api/v1/auth; Max-Age=0` (쿠키 삭제)

Redis에서 RefreshToken 삭제

### Postman 스크립트
```javascript
pm.environment.set('accessToken', '');
```

---

## 유저네임 중복 확인

**GET** `/api/v1/auth/check-username?username={username}`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "available": true
  }
}
```

---

## 이메일 중복 확인

**GET** `/api/v1/auth/check-email?email={email}`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "available": true
  }
}
```

---

## 닉네임 중복 확인

**GET** `/api/v1/auth/check-nickname?nickname={nickname}`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "available": true
  }
}
```
