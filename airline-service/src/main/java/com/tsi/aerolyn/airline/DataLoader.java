// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import org.springframework.boot.CommandLineRunner; import org.springframework.stereotype.Component;
import java.time.*; import java.util.*;
/** Seeds reference data once, then tops up itineraries so there are always flights for the next 6 months (183 days). */
@Component
class DataLoader implements CommandLineRunner {
  record Route(String o,String d,int dur,double base,boolean direct,String[] via){}
  static final List<Route> ROUTES=List.of(
    new Route("JFK","AKL",1050,2050,true,new String[]{"SFO","LAX","IAH"}), new Route("EWR","AKL",1100,1890,false,new String[]{"IAH","LAX","ORD","SFO"}),
    new Route("AKL","JFK",960,2100,true,new String[]{"LAX","SFO"}),
    new Route("AKL","SYD",190,140,true,new String[]{}), new Route("SYD","AKL",200,150,true,new String[]{}),
    new Route("AKL","WLG",70,80,true,new String[]{}), new Route("WLG","AKL",70,80,true,new String[]{}),
    new Route("AKL","CHC",95,95,true,new String[]{}), new Route("CHC","AKL",95,95,true,new String[]{}), new Route("AKL","ZQN",120,130,true,new String[]{}),
    new Route("MAA","SIN",270,210,true,new String[]{}), new Route("SIN","MAA",250,205,true,new String[]{}));
  final AirportRepo airports; final AirlineRepo airlines; final ItineraryRepo itins; final SegmentRepo segs; final FareRepo fares;
  DataLoader(AirportRepo a,AirlineRepo l,ItineraryRepo i,SegmentRepo s,FareRepo f){airports=a;airlines=l;itins=i;segs=s;fares=f;}
  static Airport ap(String c,String n,String city,String country,String tz){ Airport a=new Airport(); a.code=c;a.name=n;a.city=city;a.country=country;a.timezone=tz; return a; }
  static Airline al(String c,String n,boolean own){ Airline a=new Airline(); a.code=c;a.name=n;a.ownFleet=own; return a; }
  public void run(String... args){
    if(airports.count()==0){
      airports.saveAll(List.of(ap("AKL","Auckland Airport","Auckland","NZ","Pacific/Auckland"),ap("WLG","Wellington Airport","Wellington","NZ","Pacific/Auckland"),ap("CHC","Christchurch Airport","Christchurch","NZ","Pacific/Auckland"),
        ap("ZQN","Queenstown Airport","Queenstown","NZ","Pacific/Auckland"),ap("SYD","Sydney Airport","Sydney","AU","Australia/Sydney"),ap("JFK","John F. Kennedy Intl","New York JFK","US","America/New_York"),
        ap("EWR","Newark Liberty Intl","Newark, NJ","US","America/New_York"),ap("LAX","Los Angeles Intl","Los Angeles","US","America/Los_Angeles"),ap("SFO","San Francisco Intl","San Francisco","US","America/Los_Angeles"),
        ap("IAH","George Bush Intercontinental","Houston","US","America/Chicago"),ap("ORD","O'Hare Intl","Chicago","US","America/Chicago"),ap("MAA","Chennai Intl","Chennai","IN","Asia/Kolkata"),ap("SIN","Changi Airport","Singapore","SG","Asia/Singapore")));
      airlines.saveAll(List.of(al("AL","Aerolyn",true),al("HB","Harbour Airlines",false),al("SM","Summit Airlines",false)));
    }
    LocalDate start=Optional.ofNullable(itins.maxDate()).map(d->d.plusDays(1)).orElse(LocalDate.now()); if(start.isBefore(LocalDate.now())) start=LocalDate.now();
    Random r=new Random();
    for(LocalDate day=start;!day.isAfter(LocalDate.now().plusDays(183));day=day.plusDays(1)){ List<Itinerary> dayIts=new ArrayList<>(); List<Segment> daySegs=new ArrayList<>(); List<Fare> dayFares=new ArrayList<>(); for(Route rt:ROUTES){
      boolean lh=rt.dur>600; int n=lh?8:4;
      for(int i=0;i<n;i++){
        boolean direct=rt.via.length==0||(rt.direct&&i==1); int dep=5*60+r.nextInt(16*60); LocalDateTime d0=day.atTime(dep/60,dep%60);
        Itinerary it=new Itinerary(); it.origin=rt.o; it.dest=rt.d; it.departDate=day; it.departAt=d0; List<Segment> sl=new ArrayList<>();
        if(direct){ Segment s0=seg(it,1,rt.o,rt.d,d0,rt.dur+r.nextInt(25),0,lh,r);
          if(lh){ s0.airlineCode="AL"; s0.operatedBy="Aerolyn"; s0.aircraft="Boeing 787-9"; s0.flightNo=rt.o.equals("JFK")?"AL001":"AL002"; } sl.add(s0); }
        else { String hub=rt.via[r.nextInt(rt.via.length)]; int d1=(int)(rt.dur*.45),d2=(int)(rt.dur*.5),lay=60+r.nextInt(240); it.stops=1;
          sl.add(seg(it,1,rt.o,hub,d0,d1,lay,lh,r)); sl.add(seg(it,2,hub,rt.d,d0.plusMinutes(d1+lay),d2,0,lh,r)); }
        it.arriveAt=sl.get(sl.size()-1).arriveAt; it.durationMin=(int)Duration.between(d0,it.arriveAt).toMinutes(); dayIts.add(it);
        sl.forEach(s->s.itinerary=it); daySegs.addAll(sl);
        double eco=Math.round(rt.base*(.85+r.nextDouble()*.5)*(it.stops==0?1.1:1)); dayFares.addAll(List.of(fare(it,"ECONOMY","Q",eco,.9,r),fare(it,"PREMIUM","W",Math.round(eco*(1.25+r.nextDouble()*.6)),.95,r),fare(it,"BUSINESS","J",Math.round(eco*(2.9+r.nextDouble()*3)),.7,r)));
      } }
      itins.saveAll(dayIts); segs.saveAll(daySegs); fares.saveAll(dayFares); }
  }
  Segment seg(Itinerary it,int seq,String o,String d,LocalDateTime dep,int dur,int transit,boolean lh,Random r){
    Segment s=new Segment(); s.seq=seq; s.origin=o; s.dest=d; s.departAt=dep; s.arriveAt=dep.plusMinutes(dur); s.transitMin=transit;
    boolean own=r.nextDouble()<.6; String[] partners={"HB","SM"}; s.airlineCode=own?"AL":partners[r.nextInt(2)]; s.operatedBy=own?"Aerolyn":(s.airlineCode.equals("HB")?"Harbour Airlines":"Summit Airlines");
    s.flightNo=s.airlineCode+(100+r.nextInt(8900)); String[] big=own?new String[]{"Boeing 787-9","Boeing 777-300ER"}:new String[]{"Boeing 737-900","Airbus A321neo"};
    s.aircraft=lh?big[r.nextInt(2)]:(own?"Airbus A320":"ATR 72-600"); return s; }
  Fare fare(Itinerary it,String cabin,String cls,double price,double availProb,Random r){
    Fare f=new Fare(); f.itinerary=it; f.cabin=cabin; f.fareClass=cls; f.price=price; f.available=r.nextDouble()<availProb; f.deal=r.nextDouble()<.2; f.seatsLeft=1+r.nextInt(9); return f; }
}
