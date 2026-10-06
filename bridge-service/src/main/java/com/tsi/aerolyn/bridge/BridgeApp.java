// Copyright (c) 2026 TSI Private Limited. All rights reserved.
package com.tsi.aerolyn.bridge;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling
public class BridgeApp { public static void main(String[] a){ SpringApplication.run(BridgeApp.class,a);} }
