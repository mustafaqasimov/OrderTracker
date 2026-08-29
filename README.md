# OrderTracker
 
E-commerce order management backend with webhook-driven status updates, real-time notifications, and async email alerts.
 
**Team:** Mustafa Qasimov ([@mustafaqasimov](https://github.com/mustafaqasimov)) & Vaqif Rasulzade ([@vagifrasulzade](https://github.com/vagifrasulzade))
 
## Features
 
- JWT auth with role-based access (`USER` / `ADMIN`)
- Order CRUD with validated status transitions
- Payment & shipment webhooks with secret verification and rate limiting
- Real-time order updates via authenticated WebSocket
- Async email notifications with retry on failure
- Webhook audit logging (admin-viewable)
- CSV/Excel order export
- Admin dashboard summary
- Swagger API docs
## Tech Stack
 
Java 21 · Spring Boot 4.1 · Spring Security (JWT) · PostgreSQL + Flyway · Spring WebSocket · Spring Mail · Apache POI · Bucket4j · MapStruct · JUnit 5 / Mockito
 
## Run Locally
 
```bash
docker compose up -d          # PostgreSQL + Mailhog
```
 
Create a `.env` file in the project root:
 
```env
DB_HOST=localhost
DB_PORT=5433
DB_NAME=order_tracker
DB_USERNAME=postgres
DB_PASSWORD=your-db-password
 
JWT_SECRET=change-this-secret
WEBHOOK_SECRET=change-this-secret
 
ADMIN_USERNAME=admin
ADMIN_EMAIL=admin@ordertracker.local
ADMIN_PASSWORD=change-this-password
```
 
> No default values for secrets/credentials — the app fails fast if they're missing.
 
```bash
./mvnw spring-boot:run
```
 
App: `http://localhost:8080` · Swagger: `http://localhost:8080/swagger-ui.html`
 
```bash
./mvnw test
```
 
## API Overview
 
| Area | Path |
|---|---|
| Auth | `/api/auth` |
| Orders (user) | `/api/orders` |
| Orders (admin) | `/api/admin/orders` |
| Users (admin) | `/api/users/admin` |
| Webhooks | `/api/webhooks/payment`, `/api/webhooks/shipment` |
| Webhook logs | `/api/admin/webhook-logs` |
| WebSocket | `ws://localhost:8080/ws/orders?token={jwt}` |
 
## Known Limitations
 
- Webhook handling is synchronous (async variants exist but aren't wired in yet)
- Rate limiting is in-memory, per-instance only
- No HTTP-layer security tests yet
