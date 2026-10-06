// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import org.springframework.data.jpa.repository.JpaRepository; import java.time.Instant; import java.util.List;
interface CorrelationRepo extends JpaRepository<Correlation,String> { List<Correlation> findByStatusAndCreatedAtBefore(String s,Instant t); List<Correlation> findTop50ByOrderByCreatedAtDesc(); }
interface AuditRepo extends JpaRepository<EventAudit,Long> { List<EventAudit> findByPnrOrderById(String pnr); List<EventAudit> findTop100ByOrderByIdDesc(); }
interface RuleRepo extends JpaRepository<RoutingRule,String> {}
interface DeadRepo extends JpaRepository<DeadLetter,Long> { List<DeadLetter> findTop50ByOrderByIdDesc(); }
