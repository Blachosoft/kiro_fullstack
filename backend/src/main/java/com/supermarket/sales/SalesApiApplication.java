package com.supermarket.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SalesApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApiApplication.class, args);
    }
}
