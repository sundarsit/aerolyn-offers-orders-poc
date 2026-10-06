// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import org.springframework.stereotype.Service;
import java.time.Instant; import java.util.*;
@Service
public class SeatService {
  static final String[] COLS={"A","B","C","D","E","F","H","J","K"};
  final SeatRepo repo; final ItineraryRepo itins; SeatService(SeatRepo r,ItineraryRepo i){repo=r;itins=i;}
  /** Generates the seat map for every cabin on first request (Economy 3-3-3, Premium 2-3-2, Business 2-2). */
  public synchronized List<Seat> seats(Long id){
    List<Seat> l=repo.findByItineraryIdOrderById(id); if(!l.isEmpty()) return l;
    Itinerary it=itins.findById(id).orElseThrow(); Random r=new Random(id); List<Seat> n=new ArrayList<>();
    for(int row=1;row<=7;row++) for(String c:new String[]{"A","B","J","K"}) n.add(mk(it,"BUSINESS",row,c,"STANDARD",0,r));
    for(int row=9;row<=13;row++) for(String c:new String[]{"A","B","D","E","F","J","K"}) n.add(mk(it,"PREMIUM",row,c,"STANDARD",0,r));
    for(int row=20;row<=39;row++) for(String c:COLS){
      if(row<=21) n.add(mk(it,"ECONOMY",row,c,"LEGROOM",175,r)); else if(row<=23) n.add(mk(it,"ECONOMY",row,c,"PREFERRED",60,r));
      else if(row>=38&&"HJK".contains(c)) n.add(mk(it,"ECONOMY",row,c,"COUCHROW",0,r)); else n.add(mk(it,"ECONOMY",row,c,"STANDARD",row<=31?30:0,r)); }
    repo.saveAll(n); return repo.findByItineraryIdOrderById(id); }
  Seat mk(Itinerary it,String cabin,int row,String c,String kind,double price,Random r){
    Seat s=new Seat(); s.itinerary=it; s.cabin=cabin; s.code=row+c; s.kind=kind; s.price=price; if(r.nextDouble()<.4) s.status="BOOKED"; return s; }
  public synchronized Seat hold(Long fid,String code,String sid){
    Seat s=repo.findByItineraryIdAndCode(fid,code).orElse(null); if(s==null) return null;
    boolean free="AVAILABLE".equals(s.status)||("HELD".equals(s.status)&&(sid.equals(s.heldBy)||s.heldUntil.isBefore(Instant.now())));
    if(!free) return null; s.status="HELD"; s.heldBy=sid; s.heldUntil=Instant.now().plusSeconds(900); return repo.save(s); }
  public synchronized Seat release(Long fid,String code,String sid){
    Seat s=repo.findByItineraryIdAndCode(fid,code).orElse(null);
    if(s==null||!"HELD".equals(s.status)||!sid.equals(s.heldBy)) return null;
    s.status="AVAILABLE"; s.heldBy=null; s.heldUntil=null; return repo.save(s); }
  public synchronized List<Seat> releaseWhere(List<Seat> held){ held.forEach(s->{s.status="AVAILABLE";s.heldBy=null;s.heldUntil=null;}); return repo.saveAll(held); }
  public synchronized List<Seat> expired(){ return releaseWhere(repo.findByStatusAndHeldUntilBefore("HELD",Instant.now())); }
  public synchronized List<Seat> releaseAll(String sid){ return releaseWhere(repo.findByStatusAndHeldBy("HELD",sid)); }
  public synchronized List<Seat> confirm(Long fid,List<String> codes,String sid){
    List<Seat> out=new ArrayList<>();
    for(String c:codes){ Seat s=repo.findByItineraryIdAndCode(fid,c).orElseThrow(); if(!"HELD".equals(s.status)||!sid.equals(s.heldBy)) throw new IllegalStateException("Seat "+c+" is no longer held for you"); out.add(s);}
    out.forEach(s->{s.status="BOOKED";s.heldBy=null;s.heldUntil=null;}); return repo.saveAll(out); }
}
