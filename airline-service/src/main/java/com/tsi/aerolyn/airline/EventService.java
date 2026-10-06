// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.stereotype.Service;
import java.time.Instant; import java.util.*;
/** Sends requests to IBS (via the Bridge) and handles the responses that come back. */
@Service
class EventService {
  @Autowired EventGateway gw; @Autowired EventSocket ws; @Autowired EventLogRepo logs; @Autowired OfferRepo offers; @Autowired PnrRepo pnrs; @Autowired PaymentRepo pays;
  @Autowired AncillaryRepo ancillaries; @Autowired PassengerRepo pax; @Autowired SeatRepo seatRepo; @Autowired OrdersRepo orders; @Autowired OrderItemRepo items;
  final ObjectMapper om=new ObjectMapper();

  String request(String pnr,String type,Map<String,Object> payload){
    String corr=UUID.randomUUID().toString(); Map<String,Object> p=new HashMap<>(payload); p.put("pnr",pnr);
    Envelope e=Envelope.of(corr,type,"AIRLINE","IBS",p); log(pnr,e,"OUT","SENT"); push(pnr,"OUT",e); gw.send(e); return corr; }

  void onResponse(Envelope e){
    Map<String,Object> p=e.payload(); String pnr=(String)p.get("pnr"); boolean ok=Boolean.TRUE.equals(p.get("success"))||Boolean.TRUE.equals(p.get("available"));
    log(pnr,e,"IN","RECEIVED");
    if("PREBOOK_RESPONSE".equals(e.type())&&Boolean.TRUE.equals(p.get("success"))) saveOffer(pnr,p);
    push(pnr,"IN",e);
    if("CONFIRM_RESPONSE".equals(e.type())) finish(pnr,p,Boolean.TRUE.equals(p.get("success"))); }

  void saveOffer(String pnr,Map<String,Object> p){
    offers.findByPnrRefAndStatus(pnr,"PREBOOKED").forEach(o->{ o.status="CANCELLED"; offers.save(o); });
    Offer o=new Offer(); o.offerId=(String)p.get("offerId"); o.pnr=pnrs.findById(pnr).orElseThrow(); o.flightNo=(String)p.get("flightNo"); o.flightDate=(String)p.get("flightDate");
    o.sessionCode=(String)p.get("sessionCode"); o.itemCode=(String)p.get("nestLocation"); o.price=((Number)p.get("price")).doubleValue(); o.expiresAt=Instant.parse((String)p.get("expiresAt")); offers.save(o); }

  void finish(String pnr,Map<String,Object> p,boolean ok){
    Offer o=offers.findById(String.valueOf(p.get("offerId"))).orElse(null); Map<String,Object> m=new HashMap<>(); m.put("kind","BOOKING"); m.put("ref",pnr);
    if(ok&&o!=null){ o.status="CONFIRMED"; offers.save(o); Orders ord=createOrder(pnrs.findById(pnr).orElseThrow(),(String)p.get("orderId"));
      o.orderNumber=ord.orderNumber; offers.save(o); m.put("offerId",o.offerId); m.put("status","CONFIRMED"); m.put("orderNumber",ord.orderNumber); m.put("ibsOrderId",ord.ibsOrderId); m.put("total",ord.total); }
    else { if(o!=null){ o.status="FAILED"; offers.save(o); } pays.findByPnrRef(pnr).forEach(y->{ y.status="REFUND_PENDING"; pays.save(y); });
      m.put("status","FAILED"); m.put("reason",String.valueOf(p.getOrDefault("reason","Sleeper pod could not be confirmed. Your payment will be refunded."))); }
    ws.push(pnr,m); }

  Orders createOrder(Pnr pnr,String ibsOrderId){
    Orders o=new Orders(); o.orderNumber=(ibsOrderId!=null&&!ibsOrderId.isBlank())?ibsOrderId:"ORD"+(1000000+new Random().nextInt(8999999)); o.pnr=pnr; o.ibsOrderId=ibsOrderId; o.total=pnr.totalAmount; orders.save(o);
    List<OrderItem> l=new ArrayList<>(); l.add(item(o,"FARE",pnr.cabin+" fare",pnr.fareAmount,null)); l.add(item(o,"TAX","Taxes & charges",pnr.taxAmount,null));
    for(Passenger p:pax.findByPnrRef(pnr.ref)) if(p.seatCode!=null) seatRepo.findByItineraryIdAndCode(pnr.itinerary.id,p.seatCode).ifPresent(s->l.add(item(o,"SEAT","Seat "+s.code,s.price,null)));
    ancillaries.findByPnrRef(pnr.ref).forEach(a->l.add(item(o,a.type,a.description,a.amount,null)));
    offers.findByPnrRefAndStatus(pnr.ref,"CONFIRMED").forEach(f->l.add(item(o,"SLEEPER_POD","Session "+f.sessionCode+", "+f.itemCode,f.price,f.offerId)));
    items.saveAll(l); pnr.status="CONFIRMED"; pnrs.save(pnr); return o; }
  OrderItem item(Orders o,String t,String d,double a,String offer){ OrderItem i=new OrderItem(); i.orders=o; i.type=t; i.description=d; i.amount=a; i.offerId=offer; return i; }

  void log(String pnr,Envelope e,String dir,String st){ EventLog l=new EventLog(); l.pnr=pnr; l.correlationId=e.correlationId(); l.direction=dir; l.eventType=e.type(); l.status=st; Object of=e.payload().get("offerId"), od=e.payload().get("orderId"); l.offerId=of==null?null:of.toString(); l.orderId=od==null?null:od.toString();
    try{ l.payload=om.writeValueAsString(e.payload()); }catch(Exception ignored){} logs.save(l); }
  void push(String pnr,String dir,Envelope e){ Map<String,Object> m=new HashMap<>(); m.put("kind","EVENT"); m.put("direction",dir); m.put("eventType",e.type()); m.put("correlationId",e.correlationId()); m.put("payload",e.payload()); m.put("ts",e.timestamp()); ws.push(pnr,m); }
}
