// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.*; import org.springframework.boot.CommandLineRunner; import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Service; import org.springframework.web.socket.config.annotation.*;
import java.time.Instant; import java.util.*;
/** Routing, transformation, correlation tracking, retry/dead-letter and audit. */
@Service
class Bridge {
  @Autowired CorrelationRepo corr; @Autowired AuditRepo audit; @Autowired RuleRepo rules; @Autowired DeadRepo dead; @Autowired Out out; @Autowired MonitorSocket mon;
  @Value("${bridge.timeout-seconds}") long timeout; final ObjectMapper om=new ObjectMapper();

  void fromAirline(Envelope e){
    rec(e,"AIRLINE_TO_BRIDGE","RECEIVED");
    Correlation c=new Correlation(); c.correlationId=e.correlationId(); c.requestType=e.type(); c.originService=e.source(); c.pnr=String.valueOf(e.payload().get("pnr")); c.offerId=s(e.payload().get("offerId")); corr.save(c);
    RoutingRule r=rules.findById(e.type()).filter(x->x.enabled).orElse(null);
    if(r==null){ fail(e,"No routing rule for "+e.type(),0); return; }
    Map<String,Object> p=new HashMap<>(e.payload()); if(p.containsKey("date")) p.put("flightDate",p.remove("date")); p.put("channel","WEB");   // transformation: airline -> IBS vocabulary
    forward(new Envelope(e.eventId(),e.correlationId(),e.type(),"BRIDGE","IBS",Instant.now().toString(),p),true); }

  void fromIbs(Envelope e){
    rec(e,"IBS_TO_BRIDGE","RECEIVED"); corr.findById(e.correlationId()).ifPresent(c->{ c.status="COMPLETED"; c.completedAt=Instant.now(); c.offerId=s(e.payload().get("offerId")); c.orderId=s(e.payload().get("orderId")); corr.save(c); });
    Map<String,Object> p=new HashMap<>(e.payload()); p.put("bridgedAt",Instant.now().toString());
    forward(new Envelope(e.eventId(),e.correlationId(),e.type(),"BRIDGE","AIRLINE",Instant.now().toString(),p),false); }

  void forward(Envelope t,boolean toIbs){
    String dir=toIbs?"BRIDGE_TO_IBS":"BRIDGE_TO_AIRLINE";
    for(int a=1;a<=3;a++){
      try{ if(toIbs) out.toIbs(t); else out.toAirline(t); rec(t,dir,"FORWARDED"); return; }
      catch(Exception ex){ corr.findById(t.correlationId()).ifPresent(c->{ c.retryCount++; corr.save(c); }); rec(t,dir,"RETRY "+a); try{ Thread.sleep(500L*a);}catch(InterruptedException ie){} } }
    fail(t,"Delivery failed after 3 attempts",3); }

  void fail(Envelope e,String reason,int attempts){
    DeadLetter d=new DeadLetter(); d.eventId=e.eventId(); d.correlationId=e.correlationId(); d.eventType=e.type(); d.reason=reason; d.attempts=attempts; try{ d.payload=om.writeValueAsString(e.payload()); }catch(Exception ignored){} dead.save(d);
    corr.findById(e.correlationId()).ifPresent(c->{ c.status="FAILED"; c.completedAt=Instant.now(); corr.save(c); }); rec(e,"BRIDGE","FAILED: "+reason);
    if(e.type().endsWith("_REQUEST")) replyError(e.correlationId(),e.type(),String.valueOf(e.payload().get("pnr")),reason); }

  void replyError(String corrId,String type,String pnr,String reason){
    Map<String,Object> p=new HashMap<>(); p.put("pnr",pnr); p.put("success",false); p.put("available",false); p.put("reason",reason);
    try{ out.toAirline(Envelope.of(corrId,type.replace("_REQUEST","_RESPONSE"),"BRIDGE","AIRLINE",p)); }catch(Exception ignored){} }

  /** Anything still PENDING after the timeout gets an error response so the UI never hangs. */
  @Scheduled(fixedRate=15000) void sweep(){
    for(Correlation c:corr.findByStatusAndCreatedAtBefore("PENDING",Instant.now().minusSeconds(timeout))){ c.status="TIMEOUT"; c.completedAt=Instant.now(); corr.save(c);
      replyError(c.correlationId,c.requestType,c.pnr,"IBS did not respond in time. Please try again."); } }

  static String s(Object o){ return o==null?null:o.toString(); }
  void rec(Envelope e,String dir,String status){
    EventAudit a=new EventAudit(); a.eventId=e.eventId(); a.correlationId=e.correlationId(); a.eventType=e.type(); a.direction=dir; a.status=status; a.pnr=s(e.payload().get("pnr")); a.offerId=s(e.payload().get("offerId")); a.orderId=s(e.payload().get("orderId")); try{ a.payload=om.writeValueAsString(e.payload()); }catch(Exception ignored){}
    audit.save(a); mon.push(a); }
}
@Configuration @org.springframework.web.socket.config.annotation.EnableWebSocket
class WsConfig implements WebSocketConfigurer, CommandLineRunner {
  @Autowired MonitorSocket mon; @Autowired RuleRepo rules;
  public void registerWebSocketHandlers(WebSocketHandlerRegistry r){ r.addHandler(mon,"/ws/monitor").setAllowedOrigins("*"); }
  public void run(String... a){ if(rules.count()>0) return;
    for(String t:new String[]{"AVAILABILITY_REQUEST","PREBOOK_REQUEST","CONFIRM_REQUEST"}) rules.save(rule(t,"IBS","rename date->flightDate, add channel"));
    for(String t:new String[]{"AVAILABILITY_RESPONSE","PREBOOK_RESPONSE","CONFIRM_RESPONSE"}) rules.save(rule(t,"AIRLINE","add bridgedAt")); }
  RoutingRule rule(String t,String target,String tr){ RoutingRule r=new RoutingRule(); r.eventType=t; r.targetSystem=target; r.transformation=tr; return r; }
}
