// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import org.springframework.data.jpa.repository.*; import java.time.*; import java.util.*;
interface FlightRepo extends JpaRepository<IbsFlight,Long> { Optional<IbsFlight> findByFlightNoAndFlightDate(String n,LocalDate d); @Query("select max(f.flightDate) from IbsFlight f") LocalDate maxDate(); }
interface SessionRepo extends JpaRepository<PodSession,Long> {}
interface NestRepo extends JpaRepository<PodNest,Long> {}
interface PriceRepo extends JpaRepository<PodPrice,Long> { Optional<PodPrice> findFirstByNestIdOrderByIdDesc(Long nestId); }
interface InventoryRepo extends JpaRepository<PodInventory,Long> { List<PodInventory> findByFlightIdOrderBySessionIdAscNestIdAsc(Long fid); }
interface PrebookingRepo extends JpaRepository<Prebooking,String> { List<Prebooking> findByPnr(String pnr); List<Prebooking> findByPnrAndStatus(String pnr,String st); List<Prebooking> findByStatusAndExpiresAtBefore(String st,Instant t); List<Prebooking> findTop50ByOrderByCreatedAtDesc(); }
interface OrderRepo extends JpaRepository<IbsOrder,String> { List<IbsOrder> findByPnr(String pnr); List<IbsOrder> findTop50ByOrderByCreatedAtDesc(); }
interface OrderItemRepo extends JpaRepository<IbsOrderItem,Long> {}
