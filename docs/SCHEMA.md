# Database schema (three separate PostgreSQL databases)

## airline_db  (Airline Booking System, :8081)
| Table | Purpose | Key columns |
|---|---|---|
| airport, airline | reference data | code, name, city, country, timezone / own_fleet |
| itinerary | one bookable journey on a date | id, origin, dest, depart_date, depart_at, arrive_at, duration_min, stops, status |
| segment | each flight leg | itinerary_id, seq, flight_no, airline_code, operated_by, aircraft, origin, dest, depart_at, arrive_at, transit_min |
| fare | price per cabin | itinerary_id, cabin (ECONOMY/PREMIUM/BUSINESS), fare_class, price, currency, available, deal, seats_left |
| seat | seat map + live holds | itinerary_id, code, cabin, kind, price, status (AVAILABLE/HELD/BOOKED), held_by, held_until |
| pnr | reservation (record locator = ref) | ref, itinerary_id, cabin, status (CREATED/CONFIRMED), fare_amount, tax_amount, total_amount, contact_email, contact_phone |
| passenger | travellers on a PNR | pnr_ref, title, first/middle/last name, gender, date_of_birth, frequent_flyer, meal, assistance, seat_code |
| offer | ancillary offer issued by IBS | offer_id, pnr_ref, type, flight_no, flight_date, session_code, item_code, price, status (PREBOOKED/CONFIRMED/CANCELLED/FAILED), expires_at |
| payment | payment attempt | pnr_ref, amount, currency, reference, card_holder, card_last4, status (PAID/REFUND_PENDING) |
| ancillary | airline-sold extras | pnr_ref, type (EXTRA_BAGS/SAF_CONTRIBUTION/CAR_HOLD), description, amount |
| orders | confirmed order | order_number, pnr_ref, ibs_order_id, status, total, currency |
| order_item | order lines | orders_order_number, type (FARE/TAX/SEAT/SKYNEST/...), description, offer_id, amount |
| event_log | every event sent/received | pnr, correlation_id, direction (OUT/IN), event_type, status, payload |

## bridge_db  (Event Streaming Bridge, :8082)
| Table | Purpose | Key columns |
|---|---|---|
| correlation | one row per request/response conversation | correlation_id, request_type, origin_service, pnr, status (PENDING/COMPLETED/FAILED/TIMEOUT), retry_count, created_at, completed_at |
| event_audit | every hop through the bridge | event_id, correlation_id, event_type, direction, status, payload, created_at |
| routing_rule | event type -> target system | event_type, target_system, transformation, enabled |
| dead_letter | events that could not be delivered | event_id, correlation_id, event_type, reason, attempts, payload |

## ibs_db  (IBS, :8083)
| Table | Purpose | Key columns |
|---|---|---|
| ibs_flight | flights that sell Sleeper pod | flight_no, flight_date, origin, dest, aircraft_type, status |
| pod_session | A (earlier) / B (later) | code, label, start_offset_min, duration_min |
| pod_nest | six nest positions | location, label, level |
| pod_price | price per nest | nest_id, price, currency, valid_from, valid_to |
| pod_inventory | one row per flight x session x nest | flight_id, session_id, nest_id, status (AVAILABLE/HELD/SOLD), held_until |
| prebooking | hold + offer id | offer_id, pnr, inventory_id, price, status (PREBOOKED/CONFIRMED/CANCELLED/EXPIRED), expires_at |
| ibs_order | order id after payment | order_id, offer_id, pnr, payment_ref, total, status |
| ibs_order_item | order lines | ibs_order_order_id, item_type, description, amount |
