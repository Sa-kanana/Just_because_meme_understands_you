package com.sakana.just_because_meme_understands_you;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@MapperScan("com.sakana.just_because_meme_understands_you.mapper")
public class JustBecauseMemeUnderstandsYouApplication {

    public static void main(String[] args) {
        SpringApplication.run(JustBecauseMemeUnderstandsYouApplication.class, args);
    }

}
