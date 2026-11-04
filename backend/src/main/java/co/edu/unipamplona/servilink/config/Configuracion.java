package co.edu.unipamplona.servilink.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Parámetros del sistema. El secreto JWT no tiene valor por defecto (mínimo 32 caracteres). */
@Validated
@ConfigurationProperties("servilink")
public record Configuracion(
        @NotBlank @Size(min = 32) String jwtSecret,
        @DecimalMin("0.01") @DecimalMax("0.50") BigDecimal comision,
        @NotBlank String fcmUrl,
        boolean reintentosHabilitados,
        long reintentosCadaMs) {}
