// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.stereotype.Component; import org.springframework.web.client.RestTemplate;
import java.util.concurrent.CompletableFuture;
/** Outbound side. REST: POST to the other system. Kafka: topics ibs.requests / airline.responses. Throws on REST failure so Bridge can retry. */
@Component class Out {
  @Value("${app.transport}") String mode; @Value("${app.ibs-url}") String ibs; @Value("${app.airline-url}") String airline; @Autowired KafkaTemplate<String,String> kafka;
  final ObjectMapper om=new ObjectMapper(); final RestTemplate rest=new RestTemplate();
  void toIbs(Envelope e) throws Exception { if("kafka".equals(mode)) kafka.send("ibs.requests",e.correlationId(),om.writeValueAsString(e)); else rest.postForEntity(ibs+"/ibs/events",e,Void.class); }
  void toAirline(Envelope e) throws Exception { if("kafka".equals(mode)) kafka.send("airline.responses",e.correlationId(),om.writeValueAsString(e)); else rest.postForEntity(airline+"/api/events/callback",e,Void.class); } }
@Component @ConditionalOnProperty(name="app.transport",havingValue="kafka") class KafkaIn {
  @Autowired Bridge bridge; final ObjectMapper om=new ObjectMapper();
  @KafkaListener(topics="airline.requests",groupId="bridge") void fromAirline(String j) throws Exception { Envelope e=om.readValue(j,Envelope.class); CompletableFuture.runAsync(()->bridge.fromAirline(e)); }
  @KafkaListener(topics="ibs.responses",groupId="bridge") void fromIbs(String j) throws Exception { Envelope e=om.readValue(j,Envelope.class); CompletableFuture.runAsync(()->bridge.fromIbs(e)); } }
