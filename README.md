# baby-log-api

신생아 수유/기저귀/수면/성장 기록 Spring Boot API

## 로컬 실행

```bash
# PostgreSQL + API 같이 실행
cd ../baby-log
docker-compose up
```

API: http://localhost:8092

## 엔드포인트

| 도메인 | 메서드 | 경로 |
|--------|--------|------|
| Family | POST | `/api/v1/families` |
| Family | GET | `/api/v1/families/join/{inviteCode}` |
| Baby | POST/GET | `/api/v1/families/{familyId}/babies` |
| Feed | POST/GET | `/api/v1/babies/{babyId}/feeds` |
| Feed | PUT/DELETE | `/api/v1/babies/{babyId}/feeds/{id}` |
| Feed | GET | `/api/v1/babies/{babyId}/feeds/latest` |
| Diaper | POST/GET/DELETE | `/api/v1/babies/{babyId}/diapers` |
| Sleep | POST | `/api/v1/babies/{babyId}/sleeps/start` |
| Sleep | POST | `/api/v1/babies/{babyId}/sleeps/{id}/end` |
| Sleep | GET/DELETE | `/api/v1/babies/{babyId}/sleeps` |
| Growth Record | POST/GET/DELETE | `/api/v1/babies/{babyId}/growth-records` |
| Growth Stage | GET | `/api/v1/babies/{babyId}/growth-stage?familyId=` |
| Stats | GET | `/api/v1/babies/{babyId}/stats/today` |
| Stats | GET | `/api/v1/babies/{babyId}/stats/weekly` |

## 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `BABY_LOG_DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5492/babylog` |
| `BABY_LOG_DB_USERNAME` | DB 유저명 | `babylog` |
| `BABY_LOG_DB_PASSWORD` | DB 비밀번호 | `babylog` |
| `SERVER_PORT` | 서버 포트 | `8092` |

## fly.io 배포 (최초 1회 — 집 PC에서)

### 1. flyctl 설치 및 로그인

```bash
curl -L https://fly.io/install.sh | sh
fly auth login
```

### 2. 앱 생성

```bash
cd ~/workspace/public/lifestyle/baby-log-api
fly launch --name baby-log-api --region nrt --no-deploy
```

> `fly.toml`이 이미 있으므로 덮어쓸지 물어보면 **No** 선택

### 3. PostgreSQL 생성 및 연결

```bash
fly postgres create --name baby-log-db --region nrt --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1
fly postgres attach baby-log-db --app baby-log-api
```

attach 후 `DATABASE_URL` Secret이 자동 등록됨.

### 4. 환경변수 설정

```bash
# attach로 생긴 DATABASE_URL을 확인
fly secrets list

# BABY_LOG_DB_URL 매핑 (DATABASE_URL 값을 복사해서 사용)
fly secrets set \
  BABY_LOG_DB_URL="jdbc:postgresql://<host>:<port>/<db>" \
  BABY_LOG_DB_USERNAME="<user>" \
  BABY_LOG_DB_PASSWORD="<password>"
```

> `fly postgres attach` 후 출력되는 connection string에서 값을 확인

### 5. GitHub Secret 등록 (자동 배포용)

```bash
# API 토큰 발급
fly auth token
```

발급된 토큰을 GitHub에 등록:
`giwon1130/baby-log-api` → Settings → Secrets and variables → Actions → **New repository secret**
- Name: `FLY_API_TOKEN`
- Value: 위에서 복사한 토큰

### 6. 첫 배포

```bash
fly deploy
```

이후 `git push`만 하면 GitHub Actions가 자동 배포.

### 배포 확인

```bash
fly status
fly logs
```

앱 URL: `https://baby-log-api.fly.dev`
