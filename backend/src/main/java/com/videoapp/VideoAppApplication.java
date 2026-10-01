package com.videoapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VideoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoAppApplication.class, args);
        System.out.println("VideoAppApplication started successfully!");
    }
}
