// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.CommandLineRunner; import org.springframework.stereotype.Component; import java.time.LocalDate; import java.util.*;
/** Sleeper pod is only sold on the Boeing 787-9 long-haul flights AL001 (JFK-AKL) and AL002 (AKL-JFK), same numbers the airline uses. */
@Component class DataLoader implements CommandLineRunner {
  @Autowired FlightRepo flights; @Autowired SessionRepo sessions; @Autowired NestRepo nests; @Autowired PriceRepo prices; @Autowired InventoryRepo inventory;
  public void run(String... a){
    if(sessions.count()==0){
      sessions.save(ses("A","Session A (earlier)",120,240)); sessions.save(ses("B","Session B (later)",480,240));
      Object[][] n={{"TOP_LEFT","Top left",3,495.0},{"MIDDLE_LEFT","Middle left",2,445.0},{"BOTTOM_LEFT","Bottom left",1,395.0},{"TOP_RIGHT","Top right",3,495.0},{"MIDDLE_RIGHT","Middle right",2,445.0},{"BOTTOM_RIGHT","Bottom right",1,395.0}};
      for(Object[] x:n){ PodNest s=new PodNest(); s.location=(String)x[0]; s.label=(String)x[1]; s.level=(int)x[2]; nests.save(s);
        PodPrice p=new PodPrice(); p.nest=s; p.price=(double)x[3]; p.validFrom=LocalDate.now().minusYears(1); p.validTo=LocalDate.now().plusYears(2); prices.save(p); } }
    LocalDate start=Optional.ofNullable(flights.maxDate()).map(d->d.plusDays(1)).orElse(LocalDate.now()); if(start.isBefore(LocalDate.now())) start=LocalDate.now();
    List<PodSession> ss=sessions.findAll(); List<PodNest> ns=nests.findAll(); Random r=new Random(); List<PodInventory> inv=new ArrayList<>();
    for(LocalDate d=start;!d.isAfter(LocalDate.now().plusDays(183));d=d.plusDays(1)) for(String[] f:new String[][]{{"AL001","JFK","AKL"},{"AL002","AKL","JFK"}}){
      IbsFlight fl=new IbsFlight(); fl.flightNo=f[0]; fl.origin=f[1]; fl.dest=f[2]; fl.aircraftType="Boeing 787-9"; fl.flightDate=d; flights.save(fl);
      for(PodSession s:ss) for(PodNest n:ns){ PodInventory i=new PodInventory(); i.flight=fl; i.session=s; i.nest=n; if(r.nextDouble()<.25) i.status="SOLD"; inv.add(i); } }
    inventory.saveAll(inv); }
  PodSession ses(String c,String l,int o,int d){ PodSession s=new PodSession(); s.code=c; s.label=l; s.startOffsetMin=o; s.durationMin=d; return s; } }
