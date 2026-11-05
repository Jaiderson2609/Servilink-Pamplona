package co.edu.unipamplona.servilink.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Cuerpos de las peticiones. Los mensajes de validación se devuelven en el campo detail del problema. */
public final class Peticiones {
    private Peticiones() {}

    public record Registro(
            @NotBlank(message = "nombre es obligatorio") @Size(max = 80, message = "nombre admite hasta 80 caracteres") String nombre,
            @NotBlank(message = "correo es obligatorio") @Email(message = "correo no es válido") @Size(max = 120) String correo,
            @NotBlank(message = "telefono es obligatorio") @Pattern(regexp = "^[0-9 +]{7,20}$", message = "telefono no es válido") String telefono,
            @NotBlank(message = "clave es obligatoria") @Size(min = 8, max = 72, message = "clave debe tener entre 8 y 72 caracteres") String clave,
            @NotNull(message = "rol es obligatorio") @Pattern(regexp = "HOGAR|TECNICO", message = "rol debe ser HOGAR o TECNICO") String rol,
            @AssertTrue(message = "debes aceptar la política de datos y los términos") boolean aceptaTerminos) {}

    public record Login(
            @NotBlank(message = "correo es obligatorio") String correo,
            @NotBlank(message = "clave es obligatoria") String clave) {}

    public record Ubicacion(
            @NotNull(message = "lat es obligatoria") @DecimalMin(value = "-90", message = "lat fuera de rango") @DecimalMax(value = "90", message = "lat fuera de rango") Double lat,
            @NotNull(message = "lng es obligatoria") @DecimalMin(value = "-180", message = "lng fuera de rango") @DecimalMax(value = "180", message = "lng fuera de rango") Double lng) {}

    public record SolicitudCrear(
            @NotNull(message = "oficioId es obligatorio") @Min(value = 1, message = "oficioId no es válido") Integer oficioId,
            @NotBlank(message = "descripcion es obligatoria") @Size(min = 10, max = 500, message = "descripcion debe tener entre 10 y 500 caracteres") String descripcion,
            @NotNull(message = "ubicacion es obligatoria") @Valid Ubicacion ubicacion) {}

    public record PostulacionCrear(
            @NotNull(message = "precioCop es obligatorio") @Min(value = 1000, message = "precioCop debe ser al menos 1000") @Max(value = 5_000_000, message = "precioCop excede el máximo") Integer precioCop,
            @Size(max = 200, message = "mensaje admite hasta 200 caracteres") String mensaje) {}

    public record Cancelacion(@Size(max = 200, message = "motivo admite hasta 200 caracteres") String motivo) {}

    public record Recarga(
            @NotNull(message = "valorCop es obligatorio") @Min(value = 1000, message = "valorCop debe ser al menos 1000") @Max(value = 1_000_000, message = "valorCop excede el máximo") Long valorCop) {}

    public record ZonaTrabajo(
            @NotNull(message = "lat es obligatoria") @DecimalMin(value = "-90", message = "lat fuera de rango") @DecimalMax(value = "90", message = "lat fuera de rango") Double lat,
            @NotNull(message = "lng es obligatoria") @DecimalMin(value = "-180", message = "lng fuera de rango") @DecimalMax(value = "180", message = "lng fuera de rango") Double lng,
            @Min(value = 1, message = "el radio mínimo es 1 km") @Max(value = 50, message = "el radio máximo es 50 km") Integer radioKm) {}
}
