package com.ezetap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ezetap.server.web.portal", "com.ezetap.server.web.portal.service"})
@EnableWebMvc
public class OfferCreationAIApplication {
    public static void main(String[] args) {
        SpringApplication.run(OfferCreationAIApplication.class, args);
    }
} 