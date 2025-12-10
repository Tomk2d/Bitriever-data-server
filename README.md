# Bitriever Fetch Server

Bitriever-DAG의 Python FastAPI 서버를 Spring Boot로 마이그레이션한 서버입니다.

## 기술 스택

- Spring Boot 3.2.0
- Java 17
- PostgreSQL
- Spring Data JPA
- Spring Security + JWT
- WebClient (비동기 HTTP)
- SpringDoc OpenAPI (Swagger)

## 프로젝트 구조

```
com.bitreiver.fetch_server/
├── FetchServerApplication.java
├── domain/
│   ├── user/ (controller, service, repository, entity, dto)
│   ├── exchange/ (controller, service, repository, entity, dto)
│   ├── trading/ (controller, service, repository, entity, dto)
│   ├── asset/ (controller, service, repository, entity, dto)
│   ├── coin/ (controller, service, repository, entity, dto)
│   ├── profit/ (controller, service, repository, entity, dto)
│   └── upbit/ (controller, service)
└── global/
    ├── config/ (SecurityConfig, WebClientConfig, SwaggerConfig 등)
    ├── security/jwt/ (JwtTokenProvider, JwtAuthenticationFilter)
    ├── common/ (ApiResponse, ExceptionHandler)
    ├── controller/ (HealthController)
    └── util/ (EncryptionUtil, TimeUtil, UpbitHttpClient)
```

## 주요 기능

- 사용자 인증 (회원가입, 로그인, JWT 토큰)
- 거래소 자격증명 관리 (암호화 저장)
- Upbit API 연동 (거래내역 조회, 계정 잔고 조회)
- 거래내역 동기화 및 수익률 계산
- 자산 동기화
- 코인 목록 관리

## API 엔드포인트

### 사용자 API
- `POST /api/user/signup` - 회원가입
- `POST /api/user/login` - 로그인
- `GET /api/user/check-email` - 이메일 중복 검사
- `GET /api/user/check-nickname` - 닉네임 중복 검사
- `GET /api/user/getTradingHistory/{user_id}` - 거래내역 조회
- `POST /api/user/updateTradingHistory` - 거래내역 업데이트

### 거래소 자격증명 API
- `POST /api/exchange-credentials/{user_id}` - 자격증명 저장/업데이트
- `GET /api/exchange-credentials/{user_id}/{exchange_provider}` - 자격증명 조회
- `GET /api/exchange-credentials/{user_id}` - 모든 자격증명 조회
- `DELETE /api/exchange-credentials/{user_id}/{exchange_provider}` - 자격증명 삭제
- `POST /api/exchange-credentials/{user_id}/{exchange_provider}/verify` - 자격증명 검증

### Upbit API
- `GET /api/upbit/allTradingHistory` - 모든 거래내역 조회
- `POST /api/upbit/allCoinList` - 코인 목록 조회 및 저장
- `GET /api/upbit/accounts/{user_id}` - 계정 잔고 조회
- `POST /api/upbit/accounts` - 자산 동기화

### 수익률 API
- `POST /api/trading-profit/calculate` - 수익률 계산

## 실행 방법

1. PostgreSQL 데이터베이스 설정
2. `application.properties`에서 데이터베이스 연결 정보 설정
3. 환경변수 설정:
   - `JWT_SECRET`: JWT 토큰 생성용 시크릿 키
   - `ENCRYPTION_KEY`: 암호화용 키 (32자)
4. `./gradlew bootRun` 실행

## API 문서

서버 실행 후 `http://localhost:8081/docs`에서 Swagger UI를 통해 API 문서를 확인할 수 있습니다.

## 주의사항

- 암호화 방식이 Python의 Fernet에서 Java의 AES로 변경되었으므로, 기존 암호화된 데이터는 재입력이 필요합니다.
- 서버 포트는 기본적으로 8081로 설정되어 있습니다 (app-server는 8080).
- Upbit API 호출 시 rate limiting을 고려하여 적절한 딜레이가 포함되어 있습니다.

