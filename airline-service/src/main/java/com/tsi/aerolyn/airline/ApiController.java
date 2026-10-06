// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.time.*; import java.util.*; import java.util.concurrent.CompletableFuture; import java.util.stream.*;
@RestController @RequestMapping("/api") @CrossOrigin
public class ApiController {
  static final double TAX=66.40, BAG=80;
  @Autowired ItineraryRepo itins; @Autowired SegmentRepo segs; @Autowired FareRepo fares; @Autowired PnrRepo pnrs; @Autowired PassengerRepo pax; @Autowired PaymentRepo pays; @Autowired AncillaryRepo ancillaries;
  @Autowired OfferRepo offers; @Autowired OrdersRepo orders; @Autowired OrderItemRepo items; @Autowired EventLogRepo logs; @Autowired AirportRepo airports; @Autowired SeatService seats; @Autowired SeatSocket socket; @Autowired EventService events;
  static ResponseEntity<?> err(HttpStatus s,String m){ return ResponseEntity.status(s).body(Map.of("error",m)); }

  /* ---- 1. Search flights ---- */
  @GetMapping("/airports") List<Airport> airports(){ return airports.findAll(); }
  @GetMapping("/flights/search")
  Map<String,Object> search(@RequestParam String from,@RequestParam String to,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){
    List<Itinerary> its=itins.findByOriginAndDestAndDepartDateOrderByDepartAt(from,to,date); List<Long> ids=its.stream().map(i->i.id).toList();
    Map<Long,List<Segment>> sm=ids.isEmpty()?Map.of():segs.findByItineraryIdInOrderByItineraryIdAscSeqAsc(ids).stream().collect(Collectors.groupingBy(s->s.itinerary.id));
    Map<Long,List<Fare>> fm=ids.isEmpty()?Map.of():fares.findByItineraryIdIn(ids).stream().collect(Collectors.groupingBy(f->f.itinerary.id));
    List<Map<String,Object>> out=new ArrayList<>();
    for(Itinerary i:its){ Map<String,Object> m=new LinkedHashMap<>(); m.put("id",i.id); m.put("origin",i.origin); m.put("dest",i.dest); m.put("departAt",i.departAt); m.put("arriveAt",i.arriveAt);
      m.put("durationMin",i.durationMin); m.put("stops",i.stops); m.put("segments",sm.getOrDefault(i.id,List.of())); m.put("fares",fm.getOrDefault(i.id,List.of()).stream().collect(Collectors.toMap(f->f.cabin,f->f))); out.add(m); }
    List<Map<String,Object>> days=new ArrayList<>();
    for(int k=-3;k<=3;k++){ LocalDate d=date.plusDays(k); days.add(Map.of("date",d.toString(),"minPrice",d.isBefore(LocalDate.now())?-1:Optional.ofNullable(fares.minPrice(from,to,d)).orElse(-1.0))); }
    return Map.of("itineraries",out,"dates",days); }
  @GetMapping("/flights/{id}/seats") List<Seat> seatMap(@PathVariable Long id){ return seats.seats(id); }

  /* ---- 2. Create PNR ---- */
  @SuppressWarnings("unchecked")
  @PostMapping("/pnr") ResponseEntity<?> createPnr(@RequestBody Map<String,Object> b){
    Long id=((Number)b.get("itineraryId")).longValue(); String cabin=(String)b.get("cabin"); Map<String,Object> p=(Map<String,Object>)b.get("passenger");
    Itinerary it=itins.findById(id).orElseThrow(); Fare f=fares.findByItineraryIdAndCabin(id,cabin).orElseThrow(); if(!f.available) return err(HttpStatus.CONFLICT,"This fare is no longer available.");
    Pnr r=new Pnr(); r.ref=UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase(); r.itinerary=it; r.cabin=cabin; r.fareAmount=f.price; r.taxAmount=TAX; r.totalAmount=f.price+TAX;
    r.contactEmail=(String)p.get("email"); r.contactPhone=p.get("cc")+" "+p.get("phone"); pnrs.save(r);
    try{ pax.save(fill(new Passenger(),r,p)); }catch(RuntimeException e){ pnrs.delete(r); return err(HttpStatus.BAD_REQUEST,"Please check the date of birth."); }
    return ResponseEntity.ok(Map.of("ref",r.ref,"fareAmount",r.fareAmount,"taxAmount",r.taxAmount)); }

  Passenger fill(Passenger ps,Pnr r,Map<String,Object> p){ ps.pnr=r; ps.title=(String)p.get("title"); ps.firstName=(String)p.get("first"); ps.middleName=(String)p.get("middle"); ps.lastName=(String)p.get("last"); ps.gender=(String)p.get("gender");
    ps.dateOfBirth=LocalDate.of(Integer.parseInt((String)p.get("y")),Integer.parseInt((String)p.get("m")),Integer.parseInt((String)p.get("d"))); ps.frequentFlyer=(String)p.get("ffp"); ps.meal=(String)p.get("meal"); ps.assistance=(String)p.get("assist"); return ps; }
  /** User pressed Back to the passenger page and continued again: update the same PNR instead of creating a new one. */
  @SuppressWarnings("unchecked")
  @PutMapping("/pnr/{ref}") ResponseEntity<?> updatePnr(@PathVariable String ref,@RequestBody Map<String,Object> b){
    Pnr r=pnrs.findById(ref).orElseThrow(); if("CONFIRMED".equals(r.status)) return err(HttpStatus.CONFLICT,"This booking is already confirmed."); Map<String,Object> p=(Map<String,Object>)b.get("passenger");
    try{ pax.save(fill(pax.findByPnrRef(ref).stream().findFirst().orElse(new Passenger()),r,p)); }catch(RuntimeException e){ return err(HttpStatus.BAD_REQUEST,"Please check the date of birth."); }
    r.contactEmail=(String)p.get("email"); r.contactPhone=p.get("cc")+" "+p.get("phone"); pnrs.save(r); return ResponseEntity.ok(Map.of("ref",ref,"fareAmount",r.fareAmount,"taxAmount",r.taxAmount)); }
  /** Everything the airline knows about one PNR: same PNR / offer id / order id as Bridge and IBS. */
  @GetMapping("/trace/{ref}") Map<String,Object> trace(@PathVariable String ref){ return Map.of("pnr",pnrs.findById(ref).orElseThrow(),"passengers",pax.findByPnrRef(ref),"offers",offers.findByPnrRef(ref),"orders",orders.findByPnrRef(ref),"events",logs.findTop100ByPnrOrderByIdDesc(ref)); }

  /* ---- 3-5. Extras page: Pod availability (Airline -> Bridge -> IBS -> Bridge -> Airline -> WebSocket) ---- */
  Segment skySegment(Pnr p){ return segs.findByItineraryIdInOrderByItineraryIdAscSeqAsc(List.of(p.itinerary.id)).stream().filter(s->"AL".equals(s.airlineCode)&&s.aircraft.startsWith("Boeing 787")).findFirst().orElse(null); }
  @PostMapping("/pnr/{ref}/pod/availability") ResponseEntity<?> availability(@PathVariable String ref){
    Segment s=skySegment(pnrs.findById(ref).orElseThrow()); if(s==null) return ResponseEntity.ok(Map.of("sent",false,"reason","Sleeper pod is not offered on this journey."));
    String c=events.request(ref,"AVAILABILITY_REQUEST",Map.of("flightNo",s.flightNo,"date",s.departAt.toLocalDate().toString(),"origin",s.origin,"dest",s.dest)); return ResponseEntity.accepted().body(Map.of("sent",true,"correlationId",c)); }
  /* ---- 6-7. Select + pre-book Pod ---- */
  @PostMapping("/pnr/{ref}/pod/prebook") ResponseEntity<?> prebook(@PathVariable String ref,@RequestBody Map<String,Object> b){
    Segment s=skySegment(pnrs.findById(ref).orElseThrow()); if(s==null) return err(HttpStatus.BAD_REQUEST,"Sleeper pod is not offered on this journey.");
    String c=events.request(ref,"PREBOOK_REQUEST",Map.of("flightNo",s.flightNo,"date",s.departAt.toLocalDate().toString(),"inventoryId",b.get("inventoryId"))); return ResponseEntity.accepted().body(Map.of("correlationId",c)); }
  @PostMapping("/pnr/{ref}/pod/remove") Map<String,Object> removeSky(@PathVariable String ref){ offers.findByPnrRefAndStatus(ref,"PREBOOKED").forEach(o->{o.status="CANCELLED";offers.save(o);}); return Map.of("removed",true); }

  /* ---- 9-12. Payment, then confirm Pod with IBS, then order ---- */
  @SuppressWarnings("unchecked")
  @PostMapping("/pnr/{ref}/pay") ResponseEntity<?> pay(@PathVariable String ref,@RequestBody Map<String,Object> b){
    Pnr pnr=pnrs.findById(ref).orElseThrow(); if("CONFIRMED".equals(pnr.status)) return err(HttpStatus.CONFLICT,"This booking is already confirmed.");
    Offer offer=offers.findByPnrRefAndStatus(ref,"PREBOOKED").stream().findFirst().orElse(null);
    if(offer!=null&&offer.expiresAt.isBefore(Instant.now())) return err(HttpStatus.CONFLICT,"Your sleeper pod hold expired. Go back to Extras and select it again.");
    String sid=(String)b.get("sid"); List<String> codes=(List<String>)b.getOrDefault("seats",List.of()); List<Seat> booked;
    try{ booked=seats.confirm(pnr.itinerary.id,codes,sid);}catch(IllegalStateException e){ return err(HttpStatus.CONFLICT,e.getMessage()); }
    Map<String,Object> ex=(Map<String,Object>)b.getOrDefault("extras",Map.of()), card=(Map<String,Object>)b.getOrDefault("card",Map.of());
    ancillaries.deleteAll(ancillaries.findByPnrRef(ref)); List<Ancillary> xs=new ArrayList<>(); int bags=((Number)ex.getOrDefault("bags",0)).intValue(); double saf=((Number)ex.getOrDefault("saf",0)).doubleValue();
    if(bags>0) xs.add(anc(pnr,"EXTRA_BAGS",bags+" x 23kg",bags*BAG)); if(saf>0) xs.add(anc(pnr,"SAF_CONTRIBUTION","Sustainable aviation fuel",saf)); if(ex.get("car")!=null) xs.add(anc(pnr,"CAR_HOLD","Rental car option "+ex.get("car")+" (billed by partner)",0));
    ancillaries.saveAll(xs);
    pnr.totalAmount=pnr.fareAmount+pnr.taxAmount+booked.stream().mapToDouble(s->s.price).sum()+xs.stream().mapToDouble(a->a.amount).sum()+(offer==null?0:offer.price); pnrs.save(pnr);
    pax.findByPnrRef(ref).stream().findFirst().ifPresent(p->{ p.seatCode=codes.isEmpty()?null:codes.get(0); pax.save(p); });
    Payment py=new Payment(); py.pnr=pnr; py.amount=pnr.totalAmount; py.reference="PAY"+(100000+new Random().nextInt(899999)); py.cardHolder=(String)card.get("holder");
    String num=String.valueOf(card.getOrDefault("num","")).replaceAll("\\D",""); py.cardLast4=num.length()>=4?num.substring(num.length()-4):""; pays.save(py);
    booked.forEach(socket::broadcast);
    if(offer==null){ Orders o=events.createOrder(pnr,null); return ResponseEntity.ok(Map.of("status","CONFIRMED","orderNumber",o.orderNumber,"total",o.total,"ref",ref)); }
    events.request(ref,"CONFIRM_REQUEST",Map.of("offerId",offer.offerId,"paymentRef",py.reference,"amount",offer.price)); return ResponseEntity.accepted().body(Map.of("status","CONFIRMING")); }
  Ancillary anc(Pnr p,String t,String d,double a){ Ancillary x=new Ancillary(); x.pnr=p; x.type=t; x.description=d; x.amount=a; return x; }

  /* ---- 13. View order details ---- */
  @GetMapping("/orders/{n}") ResponseEntity<?> order(@PathVariable String n){
    return orders.findById(n).<ResponseEntity<?>>map(o->ResponseEntity.ok(Map.of("order",o,"items",items.findByOrdersOrderNumber(n),"pnr",o.pnr,"passengers",pax.findByPnrRef(o.pnr.ref),"payments",pays.findByPnrRef(o.pnr.ref),"offers",offers.findByPnrRef(o.pnr.ref)))).orElse(ResponseEntity.notFound().build()); }
  @GetMapping("/pnr/{ref}/events") List<EventLog> eventLog(@PathVariable String ref){ return logs.findTop100ByPnrOrderByIdDesc(ref); }

  /* Bridge -> Airline when app.transport=rest */
  @PostMapping("/events/callback") ResponseEntity<Void> callback(@RequestBody Envelope e){ CompletableFuture.runAsync(()->events.onResponse(e)); return ResponseEntity.accepted().build(); }
}
