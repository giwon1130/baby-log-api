# AGENTS.md — baby-log-api

AI 에이전트(Claude Code, Codex 등)가 이 레포에서 작업할 때 참고할 지침.

## 프로젝트 개요

Spring Boot 3.3 / Kotlin 1.9 / JdbcTemplate / PostgreSQL 기반 신생아 기록 API ("베이비로그" 백엔드). 현재 **Railway**에 배포되어 있고 `main` 푸쉬 시 자동 배포. 클라이언트는 [`baby-log-app`](../baby-log-app) (Expo).

## 디렉토리 맵

```
baby-log-api/
├── src/main/kotlin/com/giwon/babylog/
│   ├── bootstrap/
│   │   └── DatabaseConfig.kt       # DataSource, JdbcTemplate, 스키마 부트스트랩 (create table if not exists / alter table add column if not exists)
│   ├── features/
│   │   ├── family/                 # 가족 생성 + 초대 코드
│   │   ├── baby/                   # 아기 프로필
│   │   ├── feed/                   # 수유 기록
│   │   ├── diaper/                 # 기저귀
│   │   ├── sleep/                  # 수면
│   │   ├── growth/                 # 성장 기록 + 단계
│   │   ├── stats/                  # 오늘/주간 통계
│   │   └── cry/                    # 울음 분석 (휴리스틱 + per-baby similarity 학습)
│   │       ├── CryAnalysisController.kt
│   │       └── CryAnalysisService.kt
│   └── BabyLogApplication.kt
├── src/main/resources/application.yml
└── build.gradle.kts
```

## 도메인 요약

| 테이블 | 역할 |
|--------|------|
| `bl_families` | 가족 계정 + 초대 코드 |
| `bl_babies` | 아기 프로필 (생일, 성별, 출생 체중/키) |
| `bl_feed_records` | 수유 기록 (양/시각/모유/분유 구분, 좌/우) |
| `bl_diaper_records` | 기저귀 교체 (타입) |
| `bl_sleep_records` | 수면 시작/종료 |
| `bl_growth_records` | 체중/키/머리둘레 측정 |
| `bl_cry_samples` | 울음 분석 샘플 (음향 feature + 컨텍스트 스냅샷 + 예측/확정 라벨) |

## 울음 분류기 동작

`CryAnalysisService.classify()`는 세 가지를 합산:

1. **컨텍스트 priors** — 마지막 수유/기저귀/수면 이후 경과 시간 기반 기본 점수
2. **음향 feature 규칙** — 피치(>600Hz → PAIN), 리듬성(>0.45 → HUNGER), 낮은 음조(<250Hz → TIRED), ZCR 등
3. **per-baby similarity 부스트** — 확정 샘플 20개 이상이면 euclidean 거리로 가중치 추가

학습 단계:
- `HEURISTIC` (~20개): 규칙만
- `SIMILARITY` (20~50개): 과거 샘플 유사도 반영
- `PERSONAL` (50개+): 개인화 완료 (현재는 similarity와 동일, 추후 개인 모델 자리)

## 엔드포인트

전체 목록은 [README.md](./README.md) 참고. 주요 그룹:
- `/api/v1/families` — 가족/초대
- `/api/v1/families/{familyId}/babies` — 아기
- `/api/v1/babies/{babyId}/feeds` — 수유
- `/api/v1/babies/{babyId}/diapers` — 기저귀
- `/api/v1/babies/{babyId}/sleeps` — 수면
- `/api/v1/babies/{babyId}/growth-records` — 성장
- `/api/v1/babies/{babyId}/stats/*` — 통계
- `/api/v1/babies/{babyId}/cry-samples` — 울음 분석
- `/api/v1/cry-samples/{id}/confirm` — 라벨 확정

## 개발 워크플로우

### 로컬 실행

```bash
# Docker Compose (baby-log 모노레포 or 별도로 postgres 띄우기)
./gradlew bootRun
```

기본 포트 `8092`. DB는 `localhost:5492/babylog` (user/password 모두 `babylog`).

### 테스트

```bash
./gradlew test
```

### 스키마 마이그레이션
- 현재는 Flyway/Liquibase 안 쓰고 `DatabaseConfig.SchemaInitializer`에서 `@PostConstruct`로 `create table if not exists` / `alter table add column if not exists` 실행
- 새 컬럼 추가 시: `alter table ... add column if not exists <col> <type>` 한 줄 추가
- 컬럼 삭제/타입 변경은 수동 SQL 필요 (Railway CLI로 접속)

### 배포
- `main` 푸쉬 → Railway가 자동 빌드 + 배포
- 배포 상태는 Railway 대시보드에서 확인
- 환경변수는 Railway Secrets에서 관리

## 규칙

### 코드 스타일
- Kotlin 1.9, data class + 함수형 선호
- JdbcTemplate 직접 사용 (JPA 안 씀) — SQL은 `trimIndent()` 문자열
- DTO는 각 기능 폴더 내부에 `data class`로 정의 (별도 `dto` 패키지 없음)
- 주석은 한국어+영어 혼용 OK, "왜" 위주

### 새 엔드포인트 추가
1. `features/<domain>/` 밑에 `Service.kt` + `Controller.kt` 추가
2. 스키마 필요하면 `DatabaseConfig.SchemaInitializer.init()`에 DDL 추가
3. `baby-log-app/src/api/babyLogApi.ts`에 타입 + 함수 추가 (동반 PR)

### 커밋 메시지
- `feat:`, `fix:`, `refactor:`, `chore:` prefix
- 한국어 본문 OK
- `Co-Authored-By:` trailer 허용

## 알려진 이슈

- **JDK 17 필요**: 로컬 빌드 시. sdkman 또는 `brew install --cask temurin@17`
- **Railway cold start**: 무료 플랜 기준 최초 요청 2~3초 지연 가능
- **`bl_cry_samples` 컬럼 확장**: Phase 2A에서 pitch_mean_hz 등 6개 추가됨. 기존 샘플은 NULL.

## 로드맵

- [x] 수유/기저귀/수면/성장 기본 기능
- [x] 가족 공유 (초대 코드)
- [x] 주간/오늘 통계
- [x] 울음 분석 Phase 1 (휴리스틱 + 학습 스텁)
- [x] 울음 분석 Phase 2A (음향 feature 확장)
- [ ] 울음 분석 Phase 2B (YAMNet k-NN 하이브리드)
- [ ] 샘플 Export/백업
- [ ] 알림 푸시 (APNs — 유료 개발자 계정 필요)
