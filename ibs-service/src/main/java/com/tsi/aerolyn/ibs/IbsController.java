// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*; import java.time.LocalDate; import java.util.*; import java.util.concurrent.CompletableFuture;
@RestController @CrossOrigin
class IbsController {
  @Autowired Handler handler; @Autowired InventoryService inv; @Autowired PrebookingRepo prebookings; @Autowired OrderRepo orders;
  /** Bridge -> IBS when app.transport=rest */
  @PostMapping("/ibs/events") ResponseEntity<Void> in(@RequestBody Envelope e){ CompletableFuture.runAsync(()->handler.handle(e)); return ResponseEntity.accepted().build(); }
  @GetMapping("/api/inventory") Map<String,Object> inventory(@RequestParam String flightNo,@RequestParam String date){ return inv.availability(flightNo,LocalDate.parse(date)); }
  Map<String,Object> view(Prebooking p){ Map<String,Object> m=new LinkedHashMap<>(); m.put("offerId",p.offerId); m.put("pnr",p.pnr); m.put("orderId",p.orderId); m.put("status",p.status); m.put("price",p.price); m.put("nest",p.inventory.nest.label); m.put("session",p.inventory.session.code); m.put("flight",p.inventory.flight.flightNo+" "+p.inventory.flight.flightDate); m.put("expiresAt",p.expiresAt.toString()); return m; }
  @GetMapping("/api/prebookings") List<Map<String,Object>> pre(){ return prebookings.findTop50ByOrderByCreatedAtDesc().stream().map(this::view).toList(); }
  @GetMapping("/api/trace/{pnr}") Map<String,Object> trace(@PathVariable String pnr){ return Map.of("prebookings",prebookings.findByPnr(pnr).stream().map(this::view).toList(),"orders",orders.findByPnr(pnr)); }
  @GetMapping("/api/orders") List<IbsOrder> orders(){ return orders.findTop50ByOrderByCreatedAtDesc(); }
}
