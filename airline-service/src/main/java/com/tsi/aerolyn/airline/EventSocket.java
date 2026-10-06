// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.stereotype.Component;
import org.springframework.web.socket.*; import org.springframework.web.socket.handler.TextWebSocketHandler; import org.springframework.web.util.UriComponentsBuilder;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;
/** ws://host/ws/events?pnr=ABC123  - pushes every integration event and booking result for that PNR to the browser. */
@Component
public class EventSocket extends TextWebSocketHandler {
  final Map<String,Set<WebSocketSession>> rooms=new ConcurrentHashMap<>(); final ObjectMapper om=new ObjectMapper();
  @Override public void afterConnectionEstablished(WebSocketSession ws){ String pnr=UriComponentsBuilder.fromUri(ws.getUri()).build().getQueryParams().getFirst("pnr"); ws.getAttributes().put("pnr",pnr); rooms.computeIfAbsent(pnr,k->ConcurrentHashMap.newKeySet()).add(ws); }
  @Override public void afterConnectionClosed(WebSocketSession ws,CloseStatus s){ Set<WebSocketSession> r=rooms.get((String)ws.getAttributes().get("pnr")); if(r!=null) r.remove(ws); }
  void push(String pnr,Map<String,Object> msg){
    for(WebSocketSession w:rooms.getOrDefault(pnr,Set.of())) try{ synchronized(w){ if(w.isOpen()) w.sendMessage(new TextMessage(om.writeValueAsString(msg))); } }catch(Exception ignored){} } }
