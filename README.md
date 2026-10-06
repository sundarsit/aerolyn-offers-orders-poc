# Aerolyn – Offers & Orders POC (matches the architecture diagram)

| App | Stack | Port | Database |
|---|---|---|---|
| airline-service + airline-web (Airline Booking System) | Spring Boot + React | 8081 / 5173 | airline_db |
| bridge-service (Event Streaming Bridge) | Spring Boot (+ dashboard at :8082) | 8082 | bridge_db |
| ibs-service (IBS) | Spring Boot (+ dashboard at :8083) | 8083 | ibs_db |

## Setup (no Docker needed)
1. Install PostgreSQL, then create the 3 databases: run `db/create-databases.sql` in pgAdmin.
2. Set your Postgres password for each service (IntelliJ: Run > Edit Configurations > Environment variables `DB_PASSWORD=...`)
   or edit `spring.datasource.password` in each `application.properties`.
3. Start in this order: ibs-service, bridge-service, airline-service (run each `*App` / `App` class, or `mvn spring-boot:run` in each folder).
4. `cd airline-web && npm install && npm run dev` -> http://localhost:5173
5. Open http://localhost:8082 (bridge live audit) and http://localhost:8083 (IBS inventory) in other tabs.

## Transport: REST or Kafka
Default `app.transport=rest` (no Kafka needed): Airline -> Bridge -> IBS and back via HTTP.
For Kafka set `APP_TRANSPORT=kafka` (and `KAFKA_SERVERS=localhost:9092`) on ALL THREE services. Topics (auto-created):
`airline.requests` (Airline->Bridge), `ibs.requests` (Bridge->IBS), `ibs.responses` (IBS->Bridge), `airline.responses` (Bridge->Airline).

## End-to-end flow (13 steps) – try route JFK -> AKL, pick the direct flight (Economy or Premium)
1 Search flight -> 2 Create PNR (after passenger details) -> 3 Open Extras page -> 4 Airline requests Sleeper pod availability (AVAILABILITY_REQUEST)
-> 5 IBS answers with sessions/nests/prices, pushed to the browser over WebSocket -> 6 Select Sleeper pod -> 7 Pre-book (PREBOOK_REQUEST, IBS holds the nest 15 min)
-> 8 IBS returns Offer ID -> (seat selection) -> 9 Payment -> 10 Confirm Sleeper pod (CONFIRM_REQUEST) -> 11 IBS returns Order ID
-> 12 Booking confirmation (WebSocket) -> 13 View order details (GET /api/orders/{n}).
Sleeper pod is only sold on direct 787-9 flights AL001 (JFK-AKL) and AL002 (AKL-JFK); other flights show "not offered".

## WebSockets
- `/ws/events?pnr=` (airline): every integration event + booking result pushed live to the React UI (the "Live events" panel).
- `/ws/seats?itineraryId=&sid=` (airline): live seat holds shared between browsers.
- `/ws/monitor` (bridge) and `/ws/ibs` (ibs): feeds for the two dashboards.

## Event envelope (JSON, identical in all services)
{ eventId, correlationId, type, source, target, timestamp, payload }
Bridge: routes by `routing_rule`, renames `date` -> `flightDate`, retries 3x with back-off, dead-letters failures, and sends an error response after 60 s without an IBS reply.
See docs/SCHEMA.md for all tables.

## Six months of flights
On every start the airline and IBS services seed or top up flights for the next 183 days (about 11,000 itineraries, 33,000 fares). The first start takes a minute or two; later starts only add the new days. The date picker and day strip stop at 6 months ahead.

## Design
Original Aerolyn identity (coral / indigo / mint, rounded cards, price-bar day picker, sticky trip summary, fuselage seat map). No third-party artwork or fonts.

## Same IDs everywhere (PNR, Offer ID, Order ID)
- **PNR** is created by the airline and sent in every event, so it appears in all three systems.
- **Offer ID** is issued by IBS at pre-book and stored by the airline (`offer.offer_id`), the bridge (`event_audit.offer_id`) and IBS (`prebooking.offer_id`).
- **Order ID** is issued by IBS at confirmation and used as the airline order number too (`orders.order_number` = `ibs_order.order_id`).
  Bookings with no sleeper pod have no IBS order, so the airline issues its own `ORD…` number.
- Where to see them: the booking pages (trip panel + confirmation), Bridge dashboard :8082 (PNR / Offer ID / Order ID columns + filter), IBS dashboard :8083 (same, with filter).
- Trace one PNR through every system: `GET :8081/api/trace/{pnr}`, `GET :8082/api/trace/{pnr}`, `GET :8083/api/trace/{pnr}`.

## Navigation
Every page after Flights has a Back button; the browser Back/Forward buttons and the completed steps in the progress bar also work.
Going back to Passengers and continuing again updates the same PNR (`PUT /api/pnr/{ref}`). Choosing a different flight or cabin starts a new PNR.
