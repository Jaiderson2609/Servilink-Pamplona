package co.edu.unipamplona.servilink.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BeansConfig {

    /** Las peticiones se leen siempre como UTF-8; las respuestas no se fuerzan para no ensuciar el tipo de las imágenes. */
    @Bean
    org.springframework.web.filter.CharacterEncodingFilter codificacionUtf8() {
        var f = new org.springframework.web.filter.CharacterEncodingFilter("UTF-8", true, false);
        return f;
    }

    /** RN-03: todas las ventanas las calcula el servidor con este reloj. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
