# Ludo Online V2

MVP online multiplayer Ludo built with:
- Backend: Spring Boot 3.5, Java 17, WebSocket/STOMP, H2 by default
- Frontend: Angular standalone app
- 2–4 players per room
- Server-authoritative dice and token movement
- 6 to enter, extra turn on 6, safe cells, cutting, exact finish, winner
- Room creation/joining and live board synchronization

## Run backend
cd backend
mvn spring-boot:run

Backend: http://localhost:8080
WebSocket endpoint: ws://localhost:8080/ws

## Run frontend
cd frontend
npm install
ng serve

Frontend: http://localhost:4200

For two browser tabs, create a room in one tab and join the shown room code from the other tab.

This is an MVP foundation. Before Play Store production, add real authentication, persistent MySQL, Redis/pub-sub for multiple backend instances, rate limiting, monitoring, reconnect tokens, anti-abuse controls, privacy policy and release signing.
