// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import org.springframework.beans.factory.annotation.*; import org.springframework.stereotype.Service; import java.time.*; import java.util.*;
@Service
class InventoryService {
  @Autowired FlightRepo flights; @Autowired InventoryRepo inventory; @Autowired PriceRepo prices; @Autowired PrebookingRepo prebookings; @Autowired OrderRepo orders; @Autowired OrderItemRepo orderItems;
  @Value("${ibs.hold-minutes}") long holdMin;
  double priceOf(PodInventory i){ return prices.findFirstByNestIdOrderByIdDesc(i.nest.id).map(p->p.price).orElse(0.0); }
  static Map<String,Object> fail(String r){ Map<String,Object> m=new HashMap<>(); m.put("success",false); m.put("reason",r); return m; }

  /** Step 4-5: sessions, nests, prices and status for a flight. */
  synchronized Map<String,Object> availability(String flightNo,LocalDate date){
    IbsFlight f=flights.findByFlightNoAndFlightDate(flightNo,date).orElse(null);
    if(f==null){ Map<String,Object> m=new HashMap<>(); m.put("available",false); m.put("success",true); m.put("reason","Sleeper pod is not offered on flight "+flightNo+"."); return m; }
    Map<String,Map<String,Object>> sess=new LinkedHashMap<>(); int free=0;
    for(PodInventory i:inventory.findByFlightIdOrderBySessionIdAscNestIdAsc(f.id)){
      boolean avail="AVAILABLE".equals(i.status)||("HELD".equals(i.status)&&i.heldUntil!=null&&i.heldUntil.isBefore(Instant.now())); if(avail) free++;
      Map<String,Object> s=sess.computeIfAbsent(i.session.code,k->{ Map<String,Object> x=new LinkedHashMap<>(); x.put("code",i.session.code); x.put("label",i.session.label); x.put("startOffsetMin",i.session.startOffsetMin); x.put("durationMin",i.session.durationMin); x.put("nests",new ArrayList<Map<String,Object>>()); return x; });
      Map<String,Object> n=new LinkedHashMap<>(); n.put("inventoryId",i.id); n.put("location",i.nest.location); n.put("label",i.nest.label); n.put("price",priceOf(i)); n.put("available",avail);
      @SuppressWarnings("unchecked") List<Map<String,Object>> l=(List<Map<String,Object>>)s.get("nests"); l.add(n); }
    Map<String,Object> m=new LinkedHashMap<>(); m.put("success",true); m.put("available",free>0); m.put("flightNo",flightNo); m.put("flightDate",date.toString()); m.put("currency","USD"); m.put("availableCount",free); m.put("sessions",new ArrayList<>(sess.values()));
    if(free==0) m.put("reason","No nests left on this flight."); return m; }

  /** Step 7-8: hold a nest and issue an offer id. Re-selecting releases the PNR's previous hold. */
  synchronized Map<String,Object> prebook(String pnr,long inventoryId){
    PodInventory inv=inventory.findById(inventoryId).orElse(null); if(inv==null) return fail("Unknown nest.");
    prebookings.findByPnrAndStatus(pnr,"PREBOOKED").forEach(p->release(p,"CANCELLED"));
    inv=inventory.findById(inventoryId).orElseThrow();
    boolean free="AVAILABLE".equals(inv.status)||("HELD".equals(inv.status)&&inv.heldUntil!=null&&inv.heldUntil.isBefore(Instant.now())); if(!free) return fail("That nest is no longer available. Please choose another.");
    inv.status="HELD"; inv.heldUntil=Instant.now().plusSeconds(holdMin*60); inventory.save(inv);
    Prebooking p=new Prebooking(); p.offerId="OFF"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(); p.pnr=pnr; p.inventory=inv; p.price=priceOf(inv); p.expiresAt=inv.heldUntil; prebookings.save(p);
    Map<String,Object> m=new LinkedHashMap<>(); m.put("success",true); m.put("offerId",p.offerId); m.put("price",p.price); m.put("currency",p.currency); m.put("expiresAt",p.expiresAt.toString()); m.put("sessionCode",inv.session.code);
    m.put("nestLocation",inv.nest.location); m.put("flightNo",inv.flight.flightNo); m.put("flightDate",inv.flight.flightDate.toString()); return m; }

  /** Step 10-11: paid -> nest SOLD, order id issued. */
  synchronized Map<String,Object> confirm(String offerId,String pnr,String paymentRef){
    Prebooking p=prebookings.findById(offerId).orElse(null); Map<String,Object> m;
    if(p==null||!p.pnr.equals(pnr)) m=fail("Unknown offer."); else if(!"PREBOOKED".equals(p.status)) m=fail("Offer is "+p.status.toLowerCase()+".");
    else if(p.expiresAt.isBefore(Instant.now())){ release(p,"EXPIRED"); m=fail("The sleeper pod hold expired before payment was confirmed."); }
    else { PodInventory inv=p.inventory; inv.status="SOLD"; inv.heldUntil=null; inventory.save(inv); p.status="CONFIRMED"; prebookings.save(p);
      IbsOrder o=new IbsOrder(); o.orderId="IBS"+(1000000+new Random().nextInt(8999999)); o.offerId=offerId; o.pnr=pnr; o.paymentRef=paymentRef; o.total=p.price; orders.save(o); p.orderId=o.orderId; prebookings.save(p);
      IbsOrderItem it=new IbsOrderItem(); it.ibsOrder=o; it.itemType="SLEEPER_POD"; it.description="Session "+inv.session.code+", "+inv.nest.label+" ("+inv.flight.flightNo+" "+inv.flight.flightDate+")"; it.amount=p.price; orderItems.save(it);
      m=new LinkedHashMap<>(); m.put("success",true); m.put("orderId",o.orderId); m.put("total",o.total); }
    m.put("offerId",offerId); return m; }

  void release(Prebooking p,String status){ p.status=status; prebookings.save(p); PodInventory i=p.inventory; if("HELD".equals(i.status)){ i.status="AVAILABLE"; i.heldUntil=null; inventory.save(i); } }
  synchronized List<Prebooking> expire(){ List<Prebooking> l=prebookings.findByStatusAndExpiresAtBefore("PREBOOKED",Instant.now()); l.forEach(p->release(p,"EXPIRED")); return l; }
}
