package com.itcast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class RedbookServiceProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedbookServiceProductApplication.class, args);
    }

}
