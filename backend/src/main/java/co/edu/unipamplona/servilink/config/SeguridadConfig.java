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

    /**
     * Las métricas de Prometheus dicen cuánto tráfico y cuántos errores tiene el servicio: no son públicas.
     * Se leen con HTTP Basic (Prometheus no sabe renovar un JWT). Si METRICAS_CLAVE no está definida o tiene
     * menos de 16 caracteres, el endpoint queda cerrado para todos.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain metricas(HttpSecurity http, PasswordEncoder codificador,
                                 @org.springframework.beans.factory.annotation.Value("${servilink.metricas-usuario:prometheus}") String usuario,
                                 @org.springframework.beans.factory.annotation.Value("${servilink.metricas-clave:}") String clave)
            throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (clave == null || clave.length() < 16) {
            http.authorizeHttpRequests(a -> a.anyRequest().denyAll());
        } else {
            var cuentas = new org.springframework.security.provisioning.InMemoryUserDetailsManager(
                    org.springframework.security.core.userdetails.User.withUsername(usuario)
                            .password(codificador.encode(clave)).roles("METRICAS").build());
            http.userDetailsService(cuentas)
                    .authorizeHttpRequests(a -> a.anyRequest().hasRole("METRICAS"))
                    .httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }


    @Bean
    SecurityFilterChain cadena(HttpSecurity http, org.springframework.jdbc.core.JdbcTemplate jdbc, java.time.Clock reloj,
            co.edu.unipamplona.servilink.aplicacion.AlmacenImagenes almacen) throws Exception {
        // Si Cloudinary está configurado, el navegador puede mostrar sus imágenes y subirles directamente.
        String imagenes = almacen.disponible() ? " " + almacen.origenEntrega() : "";
        String conexiones = almacen.disponible() ? " " + almacen.origenSubida() : "";
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        var convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/auth/**").permitAll()
                        // El apretón de manos del WebSocket no lleva JWT; el cliente se autentica con su primer mensaje.
                        .requestMatchers(HttpMethod.GET, WebSocketConfig.RUTA).permitAll()
                        // Wompi no tiene JWT: la autenticidad del evento la da su firma, que verifica el controlador.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/wompi").permitAll()
                        // La plataforma web (React compilado: página, assets, manifiesto y service worker) es pública; todo lo sensible vive detrás de /api.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg", "/manifest.webmanifest", "/sw.js",
                                "/assets/**", "/icons/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-requests").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/uploads/signature").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/*/select").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-requests/*/applications").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-requests/*/confirm").hasRole("HOGAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-requests/*/applications").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-requests/*/arrive").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-requests/*/complete").hasRole("TECNICO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/service-requests/*/cancel").hasAnyRole("HOGAR", "TECNICO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-requests/open").hasRole("TECNICO")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/technicians/me/**").hasRole("TECNICO")
                        .requestMatchers("/api/v1/wallet/**", "/api/v1/wallet").hasRole("TECNICO")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint((req, res, e) -> error(res, 401, "UNAUTHENTICATED",
                                "Token ausente, vencido o inválido")))
                .exceptionHandling(h -> h.accessDeniedHandler((req, res, e) -> error(res, 403, "FORBIDDEN",
                        "No tienes permiso para esta operación")))
                .addFilterAfter(new FiltroIdempotencia(jdbc, reloj),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; "
                                + "img-src 'self' blob: data: https://tile.openstreetmap.org" + imagenes + "; connect-src 'self'" + conexiones
                                + "; object-src 'none'; base-uri 'none'; "
                                + "form-action 'self'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    JwtDecoder decodificador(Configuracion config, java.time.Clock reloj,
                             co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio usuarios) {
        var clave = new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        // El vencimiento se valida con el mismo reloj de la aplicación (así también es comprobable en pruebas).
        var tiempo = new org.springframework.security.oauth2.jwt.JwtTimestampValidator(java.time.Duration.ofSeconds(30));
        tiempo.setClock(reloj);
        // Una cuenta suspendida pierde el acceso de inmediato, sin esperar a que venza su token.
        org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> activa = token -> {
            try {
                if (Boolean.TRUE.equals(usuarios.estaSuspendido(Long.parseLong(token.getSubject())))) {
                    return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error("cuenta_suspendida", "Cuenta suspendida", null));
                }
            } catch (NumberFormatException e) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Token inválido", null));
            }
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
        };
        decodificador.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(tiempo, activa));
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

    private static void error(HttpServletResponse res, int estado, String codigo, String mensaje)
            throws java.io.IOException {
        res.setStatus(estado);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\"" + codigo + "\",\"message\":\"" + mensaje + "\",\"traceId\":\""
                + java.util.UUID.randomUUID() + "\"}");
    }
}
