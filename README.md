# baby-log-api

신생아 수유/기저귀/수면/성장/울음분석 Spring Boot API ("베이비로그" 백엔드)

Spring Boot 3.3 · Kotlin 1.9 · JdbcTemplate · PostgreSQL
배포: **Railway** (main 푸쉬 시 자동 배포)
클라이언트: [`baby-log-app`](https://github.com/giwon1130/baby-log-app) (Expo/React Native)

## 로컬 실행

```bash
# PostgreSQL + API 같이 실행 (baby-log 모노레포 구성 기준)
cd ../baby-log
docker-compose up
```

단독 실행:
```bash
./gradlew bootRun
```

API: http://localhost:8092

## 엔드포인트

| 도메인 | 메서드 | 경로 |
|--------|--------|------|
| Family | POST | `/api/v1/families` |
| Family | GET | `/api/v1/families/join/{inviteCode}` |
| Family | GET | `/api/v1/families/{familyId}` |
| Baby | POST/GET | `/api/v1/families/{familyId}/babies` |
| Baby | PUT/DELETE | `/api/v1/families/{familyId}/babies/{id}` |
| Feed | POST/GET | `/api/v1/babies/{babyId}/feeds` |
| Feed | PUT/DELETE | `/api/v1/babies/{babyId}/feeds/{id}` |
| Feed | GET | `/api/v1/babies/{babyId}/feeds/latest` |
| Diaper | POST/GET | `/api/v1/babies/{babyId}/diapers` |
| Diaper | PUT/DELETE | `/api/v1/babies/{babyId}/diapers/{id}` |
| Sleep | POST | `/api/v1/babies/{babyId}/sleeps/start` |
| Sleep | POST | `/api/v1/babies/{babyId}/sleeps/{id}/end` |
| Sleep | GET/PUT/DELETE | `/api/v1/babies/{babyId}/sleeps` |
| Sleep | GET | `/api/v1/babies/{babyId}/sleeps/active` |
| Growth Record | POST/GET/PUT/DELETE | `/api/v1/babies/{babyId}/growth-records` |
| Growth Stage | GET | `/api/v1/babies/{babyId}/growth-stage?familyId=` |
| Stats | GET | `/api/v1/babies/{babyId}/stats/today` |
| Stats | GET | `/api/v1/babies/{babyId}/stats/weekly` |
| Cry | POST | `/api/v1/babies/{babyId}/cry-samples` |
| Cry | GET | `/api/v1/babies/{babyId}/cry-samples` |
| Cry | PATCH | `/api/v1/cry-samples/{id}/confirm` |

## 울음 분석

`CryAnalysisService.classify()`가 세 가지를 합산해 라벨별 확률 산출:

1. **컨텍스트 priors** — 마지막 수유/기저귀/수면 경과, 현재 수면 중 여부
2. **음향 feature 규칙** — 피치 F0(>600Hz → PAIN), 피치 변동, 리듬성(>0.45 → HUNGER), 낮은 음조(<250Hz → TIRED), ZCR
3. **per-baby similarity 부스트** — 확정 샘플 20개 이상일 때 euclidean 거리 기반 가중치

라벨: `HUNGER` · `TIRED` · `DISCOMFORT` · `BURP` · `PAIN` · `UNKNOWN`
학습 단계: `HEURISTIC` (~20) → `SIMILARITY` (20~50) → `PERSONAL` (50+)

음성 파일은 받지 않음 — 클라이언트에서 추출한 숫자 feature만 저장.

## 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `BABY_LOG_DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5492/babylog` |
| `BABY_LOG_DB_USERNAME` | DB 유저명 | `babylog` |
| `BABY_LOG_DB_PASSWORD` | DB 비밀번호 | `babylog` |
| `SERVER_PORT` | 서버 포트 | `8092` |

## 배포 (Railway)

`main` 브랜치에 푸쉬하면 Railway가 자동으로 빌드 & 배포. 설정은 Railway 대시보드에서 관리.

### 최초 설정 (참고)

1. [Railway](https://railway.app) 가입 → New Project → Deploy from GitHub
2. `giwon1130/baby-log-api` 레포 선택 → Kotlin/Gradle 자동 감지
3. PostgreSQL plugin 추가 → `DATABASE_URL` 자동 주입
4. Variables에 `BABY_LOG_DB_URL` / `BABY_LOG_DB_USERNAME` / `BABY_LOG_DB_PASSWORD`를 DATABASE_URL 기반으로 매핑
5. 첫 배포 성공 후 도메인 생성 (예: `baby-log-api-production.up.railway.app`)

### 배포 로그 확인
Railway CLI 설치 후:
```bash
railway logs
```

## 스키마 관리

현재 Flyway/Liquibase 미사용. `bootstrap/DatabaseConfig.kt`의 `SchemaInitializer.init()`이
`@PostConstruct`로 `create table if not exists` / `alter table add column if not exists` 실행.

- 테이블 추가: `jdbcTemplate.execute("create table if not exists ...")`
- 컬럼 추가: `jdbcTemplate.execute("alter table ... add column if not exists <col> <type>")`
- 삭제/타입 변경: 수동으로 Railway DB에 접속해서 실행

## 로드맵

- [x] 수유/기저귀/수면/성장/통계 기본 기능
- [x] 가족 공유 (초대 코드)
- [x] 울음 분석 Phase 1 (휴리스틱 + 학습 스텁)
- [x] 울음 분석 Phase 2A (피치/리듬/ZCR feature 추가)
- [ ] 울음 분석 Phase 2B (YAMNet 임베딩 + Donate-a-Cry 코퍼스 k-NN)
- [ ] 데이터 Export/백업

더 자세한 에이전트용 가이드는 [AGENTS.md](./AGENTS.md) 참고.
