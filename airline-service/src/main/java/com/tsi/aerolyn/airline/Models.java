// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.*;
class Models {}
@Entity class Airport { @Id public String code; public String name, city, country, timezone; }
@Entity class Airline { @Id public String code; public String name; public boolean ownFleet; }
@Entity @Table(indexes=@Index(columnList="origin,dest,departDate"))
class Itinerary { @Id @GeneratedValue public Long id; public String origin, dest, status="SCHEDULED"; public LocalDate departDate; public LocalDateTime departAt, arriveAt; public int durationMin, stops; }
@Entity class Segment { @Id @GeneratedValue @JsonIgnore public Long id; @ManyToOne(optional=false) @JsonIgnore public Itinerary itinerary;
  public int seq, transitMin; public String flightNo, airlineCode, operatedBy, aircraft, origin, dest; public LocalDateTime departAt, arriveAt; }
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"itinerary_id","cabin"}))
class Fare { @Id @GeneratedValue @JsonIgnore public Long id; @ManyToOne(optional=false) @JsonIgnore public Itinerary itinerary;
  public String cabin, currency="USD", fareClass; public double price; public boolean available=true, deal; public int seatsLeft; }
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"itinerary_id","code"}))
class Seat { @Id @GeneratedValue @JsonIgnore public Long id; @ManyToOne(optional=false) @JsonIgnore public Itinerary itinerary;
  public String code, cabin, kind, status="AVAILABLE", heldBy; public double price; @JsonIgnore public Instant heldUntil; }
/** PNR = the reservation record (record locator in `ref`). */
@Entity class Pnr { @Id public String ref; @ManyToOne(optional=false) @JsonIgnore public Itinerary itinerary;
  public String cabin, status="CREATED", currency="USD", contactEmail, contactPhone; public double fareAmount, taxAmount, totalAmount; public Instant createdAt=Instant.now(); }
@Entity class Passenger { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) @JsonIgnore public Pnr pnr;
  public String paxType="ADULT", title, firstName, middleName, lastName, gender, frequentFlyer, meal, assistance, seatCode; public LocalDate dateOfBirth; }
/** Ancillary offer obtained from IBS (Pod). offerId is issued by IBS at pre-book. */
@Entity class Offer { @Id public String offerId; public String orderNumber; @ManyToOne(optional=false) @JsonIgnore public Pnr pnr;
  public String type="SLEEPER_POD", flightNo, flightDate, sessionCode, itemCode, status="PREBOOKED", currency="USD"; public double price; public Instant expiresAt, createdAt=Instant.now(); }
@Entity class Payment { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) @JsonIgnore public Pnr pnr;
  public double amount; public String currency="USD", reference, cardHolder, cardLast4, status="PAID"; public Instant paidAt=Instant.now(); }
@Entity class Ancillary { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) @JsonIgnore public Pnr pnr; public String type, description; public double amount; }
@Entity @Table(name="orders") class Orders { @Id public String orderNumber; @ManyToOne(optional=false) @JsonIgnore public Pnr pnr;
  public String ibsOrderId, status="CONFIRMED", currency="USD"; public double total; public Instant createdAt=Instant.now(); }
@Entity class OrderItem { @Id @GeneratedValue public Long id; @ManyToOne(optional=false) @JsonIgnore public Orders orders; public String type, description, offerId; public double amount; }
@Entity class EventLog { @Id @GeneratedValue public Long id; public String pnr, offerId, orderId, correlationId, direction, eventType, status; @Column(length=4000) public String payload; public Instant createdAt=Instant.now(); }
