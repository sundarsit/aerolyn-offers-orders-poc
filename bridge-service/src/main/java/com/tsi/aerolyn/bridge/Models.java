// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import jakarta.persistence.*; import java.time.Instant;
class Models {}
/** One row per request/response conversation. */
@Entity class Correlation { @Id public String correlationId; public String requestType, originService, pnr, offerId, orderId, status="PENDING"; public int retryCount; public Instant createdAt=Instant.now(), completedAt; }
/** Every hop an event takes through the bridge. */
@Entity class EventAudit { @Id @GeneratedValue public Long id; public String eventId, pnr, offerId, orderId, correlationId, eventType, direction, status; @Column(length=4000) public String payload; public Instant createdAt=Instant.now(); }
/** Event type -> target system. */
@Entity class RoutingRule { @Id public String eventType; public String targetSystem, transformation; public boolean enabled=true; }
@Entity class DeadLetter { @Id @GeneratedValue public Long id; public String eventId, correlationId, eventType, reason; public int attempts; @Column(length=4000) public String payload; public Instant createdAt=Instant.now(); }
