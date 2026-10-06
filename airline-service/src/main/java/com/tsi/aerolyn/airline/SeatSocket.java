// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import com.fasterxml.jackson.databind.*; import org.springframework.stereotype.Component;
import org.springframework.web.socket.*; import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
/** Real-time seat map. Client -> {type:HOLD|RELEASE, seat}. Server -> SEAT_UPDATE (to everyone viewing that flight) or DENIED (to sender). */
@Component
public class SeatSocket extends TextWebSocketHandler {
  final Map<Long,Set<WebSocketSession>> rooms=new ConcurrentHashMap<>(); final ObjectMapper om=new ObjectMapper(); final SeatService svc;
  SeatSocket(SeatService s){svc=s;}
  @Override public void afterConnectionEstablished(WebSocketSession ws){
    var q=UriComponentsBuilder.fromUri(ws.getUri()).build().getQueryParams();
    Long fid=Long.valueOf(q.getFirst("itineraryId")); ws.getAttributes().put("fid",fid); ws.getAttributes().put("sid",q.getFirst("sid"));
    rooms.computeIfAbsent(fid,k->ConcurrentHashMap.newKeySet()).add(ws); }
  @Override protected void handleTextMessage(WebSocketSession ws,TextMessage m) throws Exception {
    JsonNode n=om.readTree(m.getPayload()); Long fid=(Long)ws.getAttributes().get("fid"); String sid=(String)ws.getAttributes().get("sid"); String code=n.path("seat").asText();
    switch(n.path("type").asText()){
      case "HOLD" -> { Seat s=svc.hold(fid,code,sid); if(s==null) send(ws,Map.of("type","DENIED","seat",code)); else broadcast(s); }
      case "RELEASE" -> { Seat s=svc.release(fid,code,sid); if(s!=null) broadcast(s); }
      default -> {} } }
  @Override public void afterConnectionClosed(WebSocketSession ws,CloseStatus st){
    Set<WebSocketSession> room=rooms.get((Long)ws.getAttributes().get("fid")); if(room!=null) room.remove(ws);
    String sid=(String)ws.getAttributes().get("sid"); if(sid!=null) svc.releaseAll(sid).forEach(this::broadcast); }
  public void broadcast(Seat s){
    Map<String,Object> msg=Map.of("type","SEAT_UPDATE","seat",s.code,"status",s.status,"heldBy",s.heldBy==null?"":s.heldBy);
    for(WebSocketSession w:rooms.getOrDefault(s.itinerary.id,Set.of())) send(w,msg); }
  void send(WebSocketSession w,Map<String,Object> msg){ try{ synchronized(w){ if(w.isOpen()) w.sendMessage(new TextMessage(om.writeValueAsString(msg))); } }catch(Exception ignored){} }
}
