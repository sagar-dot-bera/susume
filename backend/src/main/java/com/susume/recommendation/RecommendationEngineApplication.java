package com.susume.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import jakarta.persistence.Cacheable;

@SpringBootApplication
@EnableCaching
public class RecommendationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationEngineApplication.class, args);
    }

}
