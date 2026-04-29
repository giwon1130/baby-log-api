# API 명세 ★ SoT

> **누가 봄**: BE / FE
> **언제 봄**: endpoint 추가/변경, 클라이언트 통합 시
> **이 문서가 SoT**. 클라이언트 호출 함수 목록은 [앱 저장소 API명세](https://github.com/giwon1130/baby-log-app/tree/main/docs/엔지니어링/API명세.md).

## Base

`/api/v1` 접두사. 모든 응답 JSON. UTF-8.

## 공통 사항

- 인증: **현재 없음**. URL의 `babyId` / `familyId` 만으로 접근.
- 시간 형식: ISO-8601 (`2026-04-26T13:45:00Z`)
- 에러: 4xx/5xx + JSON `{ "error": "..." }` 또는 raw 텍스트

## Family

### POST `/api/v1/families`
가족 생성 + 초대 코드 발급.

**Request**: 본문 없음
**Response 200**:
```json
{ "id": "uuid", "inviteCode": "ABCD1234" }
```

### GET `/api/v1/families/{familyId}`
가족 조회.

### GET `/api/v1/families/join/{inviteCode}`
초대 코드로 가족 합류 (조회).

**Response 200**: `Family` JSON
**Response 404**: 코드 없음

## Baby

### POST `/api/v1/families/{familyId}/babies`
**Request**:
```json
{
  "name": "은우",
  "birthDate": "2026-04-01",
  "gender": "FEMALE",
  "birthWeightG": 3200,
  "birthHeightCm": 50.0
}
```
**Response 200**: `Baby` JSON

### GET `/api/v1/families/{familyId}/babies`
가족의 아기 목록 (배열).

### PUT `/api/v1/families/{familyId}/babies/{babyId}`
**Request**: 모든 필드 옵셔널
```json
{ "name": "...", "birthWeightG": 3300, "birthHeightCm": 51.0 }
```

### DELETE `/api/v1/families/{familyId}/babies/{babyId}`
종속 데이터 (수유/기저귀 등) 정리는 클라이언트 책임 — 또는 향후 cascade 도입.

## Feed

### POST `/api/v1/babies/{babyId}/feeds`
**Request**:
```json
{
  "fedAt": "2026-04-26T13:00:00Z",  // optional, 미지정 시 now()
  "amountMl": 100,                   // optional
  "feedType": "FORMULA",             // FORMULA | BREAST | MIXED
  "leftMinutes": 10,                 // optional, BREAST만
  "rightMinutes": 10,                // optional
  "note": ""
}
```
**Response 200**: `FeedRecord` (next_feed_at 자동 계산 포함)

### GET `/api/v1/babies/{babyId}/feeds?limit=50&date=2026-04-26`
- `limit`: 기본 50
- `date`: YYYY-MM-DD, 해당 일자 필터

### GET `/api/v1/babies/{babyId}/feeds/latest`
가장 최근 1건.

### PUT `/api/v1/babies/{babyId}/feeds/{feedId}`
### DELETE `/api/v1/babies/{babyId}/feeds/{feedId}`

## Diaper

`/diapers/*` — Feed와 동일 패턴. `diaperType` 은 `WET|DIRTY|MIXED|DRY`.

## Sleep

### POST `/api/v1/babies/{babyId}/sleeps/start`
**Request**:
```json
{ "sleptAt": "...", "note": "" }
```
**Response 200**: `SleepRecord` with `wokeAt: null` (진행 중)

### POST `/api/v1/babies/{babyId}/sleeps/{sleepId}/end`
**Request**:
```json
{ "wokeAt": "..." }
```

### GET `/api/v1/babies/{babyId}/sleeps?limit=50`
### GET `/api/v1/babies/{babyId}/sleeps/active`
진행 중 세션 (`wokeAt IS NULL`). 없으면 `null`.

### PUT/DELETE `/api/v1/babies/{babyId}/sleeps/{sleepId}`

## Growth Record

### POST `/api/v1/babies/{babyId}/growth-records`
```json
{ "measuredAt": "...", "weightG": 4200, "heightCm": 56.0, "headCm": 38.0, "note": "" }
```
모든 측정값 옵셔널.

### GET `/api/v1/babies/{babyId}/growth-records?limit=20`
### PUT/DELETE `/api/v1/babies/{babyId}/growth-records/{id}`

## Growth Stage (자동 계산)

### GET `/api/v1/babies/{babyId}/growth-stage?familyId=...`
일령 기반 단계 + 가이드 응답.

```json
{
  "daysOld": 30,
  "stage": "NEWBORN",
  "title": "신생아기",
  "description": "...",
  "tips": ["...", "..."],
  "feedingGuideMl": { "start": 60, "end": 90 },
  "feedingIntervalHours": { "start": 2, "end": 3 }
}
```

## Stats

### GET `/api/v1/babies/{babyId}/stats/today`
```json
{
  "date": "2026-04-26",
  "feedCount": 8, "totalFeedMl": 720,
  "diaperCount": 8, "wetCount": 5, "dirtyCount": 3,
  "sleepCount": 4, "totalSleepMinutes": 480, "longestSleepMinutes": 180,
  "avgFeedIntervalMinutes": 165
}
```

### GET `/api/v1/babies/{babyId}/stats/weekly`
일별 7개 배열.

## Cry Analysis

### POST `/api/v1/babies/{babyId}/cry-samples`
음향 feature 제출 → 분류 + 저장.

**Request**:
```json
{
  "durationSec": 5.0,
  "cryConfidenceAvg": 0.78,
  "cryConfidenceMax": 0.92,
  "avgVolumeDb": -28.0,
  "peakVolumeDb": -12.0,
  "pitchMeanHz": 650.0,
  "pitchStdHz": 130.0,
  "pitchMaxHz": 950.0,
  "voicedRatio": 0.65,
  "zcrMean": 0.08,
  "rhythmicity": 0.42,
  "note": ""
}
```
모든 feature 옵셔널 — 추출 실패하면 null. `durationSec` 만 필수.

**Response 200**: `CrySampleResponse`
```json
{
  "id": "uuid",
  "babyId": "...",
  "recordedAt": "...",
  "durationSec": 5.0,
  "predictions": [
    {
      "label": "PAIN",
      "labelDisplay": "통증",
      "confidence": 0.45,
      "reasons": ["높은 음조 (650Hz)", "피치 변동 큼"]
    },
    { "label": "HUNGER", "confidence": 0.30, ... },
    ...
  ],
  "confirmedLabel": null,
  "confirmedLabelDisplay": null,
  "learningStage": {
    "confirmedCount": 12,
    "stage": "HEURISTIC",
    "stageDisplay": "신뢰도 학습 중",
    "nextStageAt": 20,
    "nextStageDisplay": "유사도 학습"
  },
  "note": ""
}
```

### PATCH `/api/v1/cry-samples/{sampleId}/confirm`
사용자가 정답 라벨 제공.

**Request**:
```json
{ "confirmedLabel": "HUNGER", "note": "" }
```
**Response 200**: 갱신된 `CrySampleResponse` (learningStage 카운트 +1)

### GET `/api/v1/babies/{babyId}/cry-samples?limit=50`
시간순 (최신순). 통계 계산은 클라이언트에서.

## 새 endpoint 추가 시

1. `features/<domain>/Controller + Service` 작성
2. 이 문서에 endpoint 표 추가 + request/response 예시
3. [`데이터모델.md`](데이터모델.md) 에 새 컬럼 / 테이블 반영
4. 클라이언트 [API명세](https://github.com/giwon1130/baby-log-app/tree/main/docs/엔지니어링/API명세.md) 에 함수 추가

## 향후 변경 예상

- 인증 헤더 (`Authorization: Bearer ...`) 추가 — [`운영/보안.md`](../운영/보안.md)
- 에러 응답 표준화 — `{ error, code, detail }`
- 페이지네이션 — `limit` 외에 `cursor` 추가
