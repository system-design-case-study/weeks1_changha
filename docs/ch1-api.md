# Chapter 1 API Contract

## 1. Nearby Search

`GET /v1/search/nearby`

Query parameters:

- `latitude` (required, decimal)
- `longitude` (required, decimal)
- `radius` (optional, meter, default `5000`)
- `limit` (optional, default `20`, max `100`)
- `cursor` (optional, pagination token)

Response `200`:

```json
{
  "total": 137,
  "nextCursor": "eyJvZmZzZXQiOjIwfQ==",
  "businesses": [
    {
      "id": 343,
      "name": "Cafe Alpha",
      "category": "CAFE",
      "distanceM": 182,
      "latitude": 37.4991,
      "longitude": 127.0313
    }
  ]
}
```

Validation rules:

- `latitude`: `[-90, 90]`
- `longitude`: `[-180, 180]`
- `radius`: `[100, 50000]`

## 2. Business Detail

`GET /v1/business/{id}`

Response `200`:

```json
{
  "id": 343,
  "ownerId": 12,
  "name": "Cafe Alpha",
  "category": "CAFE",
  "phone": "02-1234-5678",
  "address": "서울시 ...",
  "latitude": 37.4991,
  "longitude": 127.0313,
  "status": "ACTIVE",
  "updatedAt": "2026-02-09T09:30:00Z"
}
```

## 3. Business Write APIs

### `POST /v1/business`

Request body:

```json
{
  "ownerId": 12,
  "name": "Cafe Alpha",
  "category": "CAFE",
  "phone": "02-1234-5678",
  "address": "서울시 ...",
  "latitude": 37.4991,
  "longitude": 127.0313
}
```

Response `201`:

```json
{
  "id": 343
}
```

### `PUT /v1/business/{id}`

Request body: `POST`와 동일 스키마(전체 교체).
Response `200`: 갱신된 business 객체.

### `DELETE /v1/business/{id}`

Response `204`

## 4. Error Format

모든 에러는 아래 공통 포맷을 사용한다.

```json
{
  "code": "INVALID_ARGUMENT",
  "message": "radius must be between 100 and 50000",
  "requestId": "2f7c83f1..."
}
```

대표 에러 코드:

- `INVALID_ARGUMENT` (`400`)
- `NOT_FOUND` (`404`)
- `CONFLICT` (`409`)
- `INTERNAL_ERROR` (`500`)
