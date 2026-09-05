package com.hebrewproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @SpringBootApplication is itself a bundle of three annotations:
 *   @Configuration    - this class can define beans
 *   @EnableAutoConfiguration - Spring guesses sensible config from what's on
 *                        the classpath (e.g. seeing H2 + Spring Data JPA on the
 *                        classpath auto-configures an in-memory database and
 *                        EntityManager for you, no manual wiring needed)
 *   @ComponentScan    - automatically finds and registers @Component,
 *                        @Service, @Repository, @Controller classes in this
 *                        package and sub-packages (this is why WordRepository,
 *                        VerseController, etc. above don't need to be manually
 *                        registered anywhere - Spring finds them by scanning)
 */
@SpringBootApplication
public class HebrewProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(HebrewProjectApplication.class, args);
    }
}
