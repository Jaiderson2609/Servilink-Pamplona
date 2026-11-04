package co.edu.unipamplona.servilink.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BeansConfig {

    /** RN-03: todas las ventanas las calcula el servidor con este reloj. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
