package co.edu.unipamplona.servilink.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/** JWT firmado con HS256. El rol viaja en la reclamación "rol" y el usuario en "sub". */
@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        var convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/asignacion").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/solicitudes/*/postulaciones").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/confirmacion").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/postulaciones").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/llegada").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/trabajo-terminado").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes/*/cancelacion").hasAnyRole("HOGAR", "TECNICO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/solicitudes/abiertas").hasRole("TECNICO")
                        .requestMatchers("/api/v1/tecnicos/**").hasRole("TECNICO")
                        .requestMatchers("/api/v1/billetera/**", "/api/v1/billetera").hasRole("TECNICO")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint((req, res, e) -> problema(res, 401, "No autenticado",
                                "Token ausente, vencido o inválido")))
                .exceptionHandling(h -> h.accessDeniedHandler((req, res, e) -> problema(res, 403, "Sin permiso",
                        "No tienes permiso para esta operación")))
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    JwtDecoder decodificador(Configuracion config, java.time.Clock reloj) {
        var clave = new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        // El vencimiento se valida con el mismo reloj de la aplicación (así también es comprobable en pruebas).
        var tiempo = new org.springframework.security.oauth2.jwt.JwtTimestampValidator(java.time.Duration.ofSeconds(30));
        tiempo.setClock(reloj);
        decodificador.setJwtValidator(tiempo);
        return decodificador;
    }

    @Bean
    org.springframework.security.oauth2.jwt.JwtEncoder emisor(Configuracion config) {
        var clave = new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableSecret<>(clave));
    }

    @Bean
    PasswordEncoder codificador() {
        return new BCryptPasswordEncoder();
    }

    private static void problema(HttpServletResponse res, int estado, String titulo, String detalle)
            throws java.io.IOException {
        res.setStatus(estado);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        String tipo = "https://servilink.co/errores/" + titulo.toLowerCase().replace(' ', '-');
        res.getWriter().write("{\"type\":\"" + tipo + "\",\"title\":\"" + titulo + "\",\"status\":" + estado
                + ",\"detail\":\"" + detalle + "\"}");
    }
}
