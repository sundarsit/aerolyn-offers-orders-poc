// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import org.springframework.data.jpa.repository.*; import java.time.*; import java.util.*;
interface AirportRepo extends JpaRepository<Airport,String> {}
interface AirlineRepo extends JpaRepository<Airline,String> {}
interface ItineraryRepo extends JpaRepository<Itinerary,Long> {
  List<Itinerary> findByOriginAndDestAndDepartDateOrderByDepartAt(String o,String d,LocalDate date);
  @Query("select max(i.departDate) from Itinerary i") LocalDate maxDate(); }
interface SegmentRepo extends JpaRepository<Segment,Long> { List<Segment> findByItineraryIdInOrderByItineraryIdAscSeqAsc(Collection<Long> ids); }
interface FareRepo extends JpaRepository<Fare,Long> {
  List<Fare> findByItineraryIdIn(Collection<Long> ids); Optional<Fare> findByItineraryIdAndCabin(Long id,String cabin);
  @Query("select min(f.price) from Fare f where f.itinerary.origin=?1 and f.itinerary.dest=?2 and f.itinerary.departDate=?3 and f.available=true") Double minPrice(String o,String d,LocalDate date); }
interface SeatRepo extends JpaRepository<Seat,Long> {
  List<Seat> findByItineraryIdOrderById(Long id); Optional<Seat> findByItineraryIdAndCode(Long id,String c);
  List<Seat> findByStatusAndHeldUntilBefore(String s,Instant t); List<Seat> findByStatusAndHeldBy(String s,String by); }
interface PnrRepo extends JpaRepository<Pnr,String> {}
interface PassengerRepo extends JpaRepository<Passenger,Long> { List<Passenger> findByPnrRef(String ref); }
interface OfferRepo extends JpaRepository<Offer,String> { List<Offer> findByPnrRef(String ref); List<Offer> findByPnrRefAndStatus(String ref,String st); }
interface PaymentRepo extends JpaRepository<Payment,Long> { List<Payment> findByPnrRef(String ref); }
interface AncillaryRepo extends JpaRepository<Ancillary,Long> { List<Ancillary> findByPnrRef(String ref); }
interface OrdersRepo extends JpaRepository<Orders,String> { List<Orders> findByPnrRef(String ref); }
interface OrderItemRepo extends JpaRepository<OrderItem,Long> { List<OrderItem> findByOrdersOrderNumber(String n); }
interface EventLogRepo extends JpaRepository<EventLog,Long> { List<EventLog> findTop100ByPnrOrderByIdDesc(String pnr); }
