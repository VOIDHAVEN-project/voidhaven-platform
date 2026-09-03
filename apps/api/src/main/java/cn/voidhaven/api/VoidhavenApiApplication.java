package cn.voidhaven.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoidhavenApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoidhavenApiApplication.class, args);
    }
}
