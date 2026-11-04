package co.edu.unipamplona.servilink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServilinkBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServilinkBackendApplication.class, args);
    }
}
