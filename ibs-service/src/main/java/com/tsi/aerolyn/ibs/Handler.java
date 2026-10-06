// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.ibs;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration; import org.springframework.kafka.annotation.KafkaListener; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.*; import org.springframework.web.client.RestTemplate; import org.springframework.web.socket.*; import org.springframework.web.socket.config.annotation.*; import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.LocalDate; import java.util.*; import java.util.concurrent.*;
/** Processes requests and replies (REST: POST bridge /bridge/responses, Kafka: topic ibs.responses). */
@Service class Handler {
  @Autowired InventoryService inv; @Autowired IbsSocket ws; @Autowired KafkaTemplate<String,String> kafka; @Value("${app.transport}") String mode; @Value("${app.bridge-url}") String bridge;
  final ObjectMapper om=new ObjectMapper(); final RestTemplate rest=new RestTemplate();
  void handle(Envelope e){
    Map<String,Object> p=e.payload(), r;
    try{ r=switch(e.type()){
        case "AVAILABILITY_REQUEST"->inv.availability((String)p.get("flightNo"),LocalDate.parse((String)p.get("flightDate")));
        case "PREBOOK_REQUEST"->inv.prebook((String)p.get("pnr"),((Number)p.get("inventoryId")).longValue());
        case "CONFIRM_REQUEST"->inv.confirm((String)p.get("offerId"),(String)p.get("pnr"),(String)p.get("paymentRef"));
        default->InventoryService.fail("Unsupported event "+e.type()); };
    }catch(Exception ex){ r=InventoryService.fail(String.valueOf(ex.getMessage())); }
    Map<String,Object> res=new HashMap<>(r); res.put("pnr",p.get("pnr"));
    Envelope out=Envelope.of(e.correlationId(),e.type().replace("_REQUEST","_RESPONSE"),"IBS","AIRLINE",res);
    try{ if("kafka".equals(mode)) kafka.send("ibs.responses",out.correlationId(),om.writeValueAsString(out)); else rest.postForEntity(bridge+"/bridge/responses",out,Void.class); }catch(Exception x){ System.err.println("Could not reply: "+x.getMessage()); }
    Map<String,Object> n=new HashMap<>(); n.put("event",e.type()); n.put("pnr",p.get("pnr")); n.put("success",res.get("success")); ws.push(n); } }
@Component @ConditionalOnProperty(name="app.transport",havingValue="kafka") class KafkaIn {
  @Autowired Handler h; final ObjectMapper om=new ObjectMapper();
  @KafkaListener(topics="ibs.requests",groupId="ibs") void in(String j) throws Exception { Envelope e=om.readValue(j,Envelope.class); CompletableFuture.runAsync(()->h.handle(e)); } }
/** ws://localhost:8083/ws/ibs - live feed for the IBS dashboard. */
@Component class IbsSocket extends TextWebSocketHandler {
  final Set<WebSocketSession> all=ConcurrentHashMap.newKeySet(); final ObjectMapper om=new ObjectMapper();
  @Override public void afterConnectionEstablished(WebSocketSession s){ all.add(s); } @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus c){ all.remove(s); }
  void push(Object o){ for(WebSocketSession w:all) try{ synchronized(w){ if(w.isOpen()) w.sendMessage(new TextMessage(om.writeValueAsString(o))); } }catch(Exception ignored){} } }
@Configuration @EnableWebSocket class WsConfig implements WebSocketConfigurer {
  @Autowired IbsSocket sock; @Autowired InventoryService inv;
  public void registerWebSocketHandlers(WebSocketHandlerRegistry r){ r.addHandler(sock,"/ws/ibs").setAllowedOrigins("*"); }
  @Scheduled(fixedRate=30000) void expire(){ for(Prebooking p:inv.expire()) sock.push(Map.of("event","HOLD_EXPIRED","pnr",String.valueOf(p.pnr),"success",true)); } }
