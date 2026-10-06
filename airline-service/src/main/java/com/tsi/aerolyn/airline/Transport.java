// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.stereotype.Component; import org.springframework.web.client.RestTemplate;
import java.util.concurrent.CompletableFuture;
/** Airline -> Bridge. REST: POST /bridge/events. Kafka: topic airline.requests. */
@Component class EventGateway {
  @Value("${app.transport}") String mode; @Value("${app.bridge-url}") String bridge; @Autowired KafkaTemplate<String,String> kafka;
  final ObjectMapper om=new ObjectMapper(); final RestTemplate rest=new RestTemplate();
  void send(Envelope e){
    try{ if("kafka".equals(mode)) kafka.send("airline.requests",e.correlationId(),om.writeValueAsString(e));
      else CompletableFuture.runAsync(()->{ try{ rest.postForEntity(bridge+"/bridge/events",e,Void.class);}catch(Exception x){ System.err.println("Bridge unreachable: "+x.getMessage()); } });
    }catch(Exception x){ throw new RuntimeException(x); } } }
/** Bridge -> Airline over Kafka (topic airline.responses). In REST mode the Bridge calls POST /api/events/callback instead. */
@Component @ConditionalOnProperty(name="app.transport",havingValue="kafka") class KafkaIn {
  @Autowired EventService events; final ObjectMapper om=new ObjectMapper();
  @KafkaListener(topics="airline.responses",groupId="airline") void in(String json) throws Exception { events.onResponse(om.readValue(json,Envelope.class)); } }
