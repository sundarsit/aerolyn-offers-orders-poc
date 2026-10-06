// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.stereotype.Component; import org.springframework.web.socket.*; import org.springframework.web.socket.handler.TextWebSocketHandler; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
/** ws://localhost:8082/ws/monitor - live audit feed for the bridge dashboard. */
@Component class MonitorSocket extends TextWebSocketHandler {
  final Set<WebSocketSession> all=ConcurrentHashMap.newKeySet(); final ObjectMapper om=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  @Override public void afterConnectionEstablished(WebSocketSession s){ all.add(s); } @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus c){ all.remove(s); }
  void push(Object o){ for(WebSocketSession w:all) try{ synchronized(w){ if(w.isOpen()) w.sendMessage(new TextMessage(om.writeValueAsString(o))); } }catch(Exception ignored){} } }
