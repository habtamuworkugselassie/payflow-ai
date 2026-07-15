# PayFlow AI Full Demo

This corrected version includes:

- Real Vue 3 + TypeScript frontend
- API-driven user creation
- API-driven provider-account linking
- Both senders and receivers can link multiple accounts
- No hardcoded sender or receiver accounts
- Smart Pay and Smart Settlement
- Source × route × destination corridor ranking
- Provider failover
- In-memory storage for hackathon demo simplicity

## Run

```bash
docker compose up --build
```

Open:

```text
http://localhost:5173
```

Backend API:

```text
http://localhost:8080
```

## Main APIs

Create user:

```http
POST /api/v1/users
```

Link provider account:

```http
POST /api/v1/users/{userId}/accounts
```

List user accounts:

```http
GET /api/v1/users/{userId}/accounts
```

Create smart payment:

```http
POST /api/v1/payments
```

The source owner and destination owner can each register and link multiple provider accounts using the API or Vue UI.
