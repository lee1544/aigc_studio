package com.example.aigc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class AigcStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AigcStudioApplication.class, args);
    }

}
