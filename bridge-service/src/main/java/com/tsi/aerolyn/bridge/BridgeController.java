// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*; import java.util.*; import java.util.concurrent.CompletableFuture;
@RestController @CrossOrigin
class BridgeController {
  @Autowired Bridge bridge; @Autowired AuditRepo audit; @Autowired CorrelationRepo corr; @Autowired DeadRepo dead; @Autowired RuleRepo rules;
  @PostMapping("/bridge/events") ResponseEntity<Void> fromAirline(@RequestBody Envelope e){ CompletableFuture.runAsync(()->bridge.fromAirline(e)); return ResponseEntity.accepted().build(); }
  @PostMapping("/bridge/responses") ResponseEntity<Void> fromIbs(@RequestBody Envelope e){ CompletableFuture.runAsync(()->bridge.fromIbs(e)); return ResponseEntity.accepted().build(); }
  @GetMapping("/api/trace/{pnr}") List<EventAudit> trace(@PathVariable String pnr){ return audit.findByPnrOrderById(pnr); }
  @GetMapping("/api/audit") List<EventAudit> audit(){ return audit.findTop100ByOrderByIdDesc(); }
  @GetMapping("/api/correlations") List<Correlation> corr(){ return corr.findTop50ByOrderByCreatedAtDesc(); }
  @GetMapping("/api/dead-letters") List<DeadLetter> dead(){ return dead.findTop50ByOrderByIdDesc(); }
  @GetMapping("/api/routing-rules") List<RoutingRule> rules(){ return rules.findAll(); }
}
