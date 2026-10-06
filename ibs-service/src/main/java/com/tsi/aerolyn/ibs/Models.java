// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import jakarta.persistence.*; import java.time.*;
class Models {}
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"flightNo","flightDate"}))
class IbsFlight { @Id @GeneratedValue public Long id; public String flightNo, origin, dest, aircraftType, status="SCHEDULED"; public LocalDate flightDate; }
@Entity class PodSession { @Id @GeneratedValue public Long id; public String code, label; public int startOffsetMin, durationMin; }
@Entity class PodNest { @Id @GeneratedValue public Long id; public String location, label; public int level; }
@Entity class PodPrice { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) public PodNest nest; public double price; public String currency="USD"; public LocalDate validFrom, validTo; }
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"flight_id","session_id","nest_id"}))
class PodInventory { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) public IbsFlight flight; @ManyToOne(optional=false) public PodSession session; @ManyToOne(optional=false) public PodNest nest; public String status="AVAILABLE"; public Instant heldUntil; }
@Entity class Prebooking { @Id public String offerId; public String pnr, orderId, status="PREBOOKED", currency="USD"; @ManyToOne(optional=false) public PodInventory inventory; public double price; public Instant expiresAt, createdAt=Instant.now(); }
@Entity class IbsOrder { @Id public String orderId; public String offerId, pnr, status="CONFIRMED", paymentRef, currency="USD"; public double total; public Instant createdAt=Instant.now(); }
@Entity class IbsOrderItem { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) public IbsOrder ibsOrder; public String itemType, description; public double amount; }
