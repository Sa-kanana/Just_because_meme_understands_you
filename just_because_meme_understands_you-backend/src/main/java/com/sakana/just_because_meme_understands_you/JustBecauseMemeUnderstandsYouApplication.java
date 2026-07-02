package com.sakana.just_because_meme_understands_you;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sakana.just_because_meme_understands_you.mapper")
@EnableScheduling
public class JustBecauseMemeUnderstandsYouApplication {

    public static void main(String[] args) {
        SpringApplication.run(JustBecauseMemeUnderstandsYouApplication.class, args);
    }

}
