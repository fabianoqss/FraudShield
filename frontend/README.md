# FraudShield frontend

FraudShield is an English-language interface for simulated payments with fraud detection. It uses React, Vite, TypeScript in strict mode, React Router, and plain CSS. The client in `src/api/client.ts` uses `fetch` for API calls.

## Install and run

Use Node.js 22.12 or later and npm. From `frontend/`, run the following commands.

```bash
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The `package-lock.json` file records the dependency versions.

## Backend requirements

Run the backend infrastructure (PostgreSQL, MongoDB, Kafka, and Redis) along with auth-service, account-service, transaction-service, fraud-detection-service, ml-model-service, ledger-service, and api-gateway on port 8080. The notification-service completes the backend notification workflow. See the backend README for startup instructions and environment variables.

The Vite proxy forwards `/auth`, `/accounts`, `/transactions`, and `/ledger` to `http://localhost:8080`. The app calls the gateway for all requests and runs without a mock server.

The API contract is in `../docs/API.md`. If an endpoint is unavailable, the interface shows an error and lets you retry.

## Validation commands

```bash
npm run build
npm test
npm run typecheck
npm run test:watch
```

The build outputs to `dist/`. To deploy that directory, configure your server to forward the four API route groups to the gateway and serve `index.html` as the fallback for frontend routes. The Vite proxy runs only in development.

## Features

- Registration with field-level errors, sign-in, and a 15-minute lockout notice on HTTP 429.
- Protected routes and session restoration on page reload.
- Account listing, selection, and creation when no account exists. View total, locked, and available balances and copy the account ID.
- My Pix keys (`/pix-keys`) lists keys for the selected account. Register a CPF, email, or random key, copy keys, and confirm removal. Each account can have up to five keys.
- Simulated Pix deposits use a registered email, CPF, or random key. Unregistered keys trigger a specific error.
- Pix, credit, and debit transfers use a key lookup. Confirm the recipient's name and masked CPF, then track progress every 1.5 seconds for up to 30 seconds.
- Paginated transfer statement with source, destination, and status, plus transfer details and audit events with fraud scoring.
- Sign out manually or automatically after changing your password.

## Authentication and retries

The access token stays in memory and the refresh token stays in `sessionStorage`. The frontend reads the user's name and ID from the JWT's `fullName` and `sub` claims. The backend handles authorization.

The client shares a single refresh promise across concurrent requests, including during session restoration. After an HTTP 401, it retries the authenticated request at most once. A delayed 401 can use the already refreshed access token. If refresh fails, the client clears the session and redirects to sign-in. It never automatically resends the old refresh token.

An HTTP 401 when changing a password can also mean the current password is incorrect. After one refresh and one retry, the form shows the error and keeps the valid session active. Sign-out waits for an in-flight refresh so it can revoke the latest token.

Each new transfer submission gets a UUID. If the network fails or the response can't be read, the form locks the original details. “Retry same submission” reuses the original payload and idempotency key. Leaving the page discards the pending submission. Check the statement before starting another transfer. The app creates `deviceId` once and stores it in `localStorage`. It sends `ipAddress` as `null`.

## API contract and limitations

- Key registration sends only `{ type }`. The server supplies the value. If the account already has a CPF or email key, that registration option is disabled. An account can have multiple distinct random keys within the five-key limit.
- The key being looked up is sent only in the body of `POST /accounts/pix-keys/lookup`. The form confirms the recipient's name and masked CPF without receiving or storing the destination account ID. It sends `lookupId` in `POST /transactions` for every payment type.
- The `lookupId` is valid for five minutes. The frontend uses `expiresAt` to return to the lookup step when it expires, keeping the key and amount. A server response indicating an expired or invalid lookup does the same. Going back or starting a new transfer requires a fresh lookup.
- On HTTP 429 during lookup, the button stays disabled for the number of seconds in `Retry-After` and the interface displays a countdown. If the header is missing or invalid, the app uses the 60-second window documented in the API contract.
- Retrying an unconfirmed submission preserves the entire original payload, including an expired `lookupId`. The backend checks idempotency before validating the lookup, so the retry keeps the original lookup.
- The contract retains `destinationAccountId` in transaction responses and history. The statement uses that field. Key lookups and transfer requests omit it.
- HTTP 422 responses share a status code. The frontend recognizes invalid or expired lookups and same-account transfers from the documented backend messages. It displays `message` as returned for errors it doesn't recognize.
- Reusing an accepted idempotency key returns HTTP 409 without the original transaction ID. The interface asks you to check the statement for the outcome of the first attempt.
- At the end of the 30-second polling window, the transfer is still pending review. Navigating away stops polling.
- The interface displays `FLAGGED` as “IN REVIEW” and the funds remain on hold. The backend has no manual review workflow.
- The ledger has no server-side transaction filter. The frontend filters each fetched page by `transactionId` and offers “Search more pages” when more pages are available. Later pages can contain events for the transaction even if the pages searched so far contain none.
- Simulated deposits are excluded from the transfer statement and ledger.
- Balances, statuses, and events update asynchronously. You can refresh them in the interface.
- The frontend formats dates and BRL amounts using the `en-US` locale. The browser interprets dates without a time zone offset in its local time zone.
- The frontend displays its own labels, instructions, and messages in English. Structured backend messages (`message` and `fieldErrors`) retain the server's wording.

## Tests

Vitest and Testing Library run in jsdom. The tests cover shared token refresh, delayed 401 responses, retry limits, refresh failures, session restoration, concurrent sign-out, accented characters in JWTs, 204 responses, non-JSON errors, registration and sign-in, password changes, Pix key registration, listing and removal, the five-key limit, deposits with registered keys, transfer lookup and confirmation, local lookup expiration and HTTP 422, the `Retry-After` countdown, HTTP 404, 409, and 503 errors, idempotency after network failures, polling, cancellation, and ledger pagination.

The tests replace the HTTP transport or client methods within the test process, without starting a mock server. End-to-end validation requires running all backend services.
