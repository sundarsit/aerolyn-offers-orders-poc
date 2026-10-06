// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import org.springframework.context.annotation.Configuration; import org.springframework.scheduling.annotation.*; import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket @EnableScheduling
class Config implements WebSocketConfigurer {
  final SeatSocket socket; final SeatService svc; final EventSocket events; Config(SeatSocket s,SeatService v,EventSocket e){socket=s;svc=v;events=e;}
  public void registerWebSocketHandlers(WebSocketHandlerRegistry r){ r.addHandler(socket,"/ws/seats").setAllowedOrigins("*"); r.addHandler(events,"/ws/events").setAllowedOrigins("*"); }
  @Scheduled(fixedRate=30000) void expireHolds(){ svc.expired().forEach(socket::broadcast); }
}
