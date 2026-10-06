// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.airline;
import java.time.Instant; import java.util.*;
/** Message exchanged between Airline, Bridge and IBS (same shape in all three services). */
public record Envelope(String eventId,String correlationId,String type,String source,String target,String timestamp,Map<String,Object> payload){
  static Envelope of(String corr,String type,String src,String tgt,Map<String,Object> p){ return new Envelope(UUID.randomUUID().toString(),corr,type,src,tgt,Instant.now().toString(),p); } }
