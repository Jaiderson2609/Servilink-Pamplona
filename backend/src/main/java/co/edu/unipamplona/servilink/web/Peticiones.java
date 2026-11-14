package co.edu.unipamplona.servilink.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Cuerpos de las peticiones. Los mensajes de validación se devuelven en el campo message del error. */
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

    public record OfertaCrear(
            @NotNull(message = "montoCop es obligatorio") @Min(value = 1000, message = "montoCop debe ser al menos 1000") @Max(value = 5_000_000, message = "montoCop excede el máximo") Integer montoCop) {}

    public record Disponibilidad(
            @NotNull(message = "estado es obligatorio") @Pattern(regexp = "AVAILABLE|BUSY|OFFLINE", message = "estado debe ser AVAILABLE, BUSY u OFFLINE") String estado) {}

    public record Cancelacion(@Size(max = 200, message = "motivo admite hasta 200 caracteres") String motivo) {}

    public record Recarga(
            @NotNull(message = "valorCop es obligatorio") @Min(value = 1000, message = "valorCop debe ser al menos 1000") @Max(value = 1_000_000, message = "valorCop excede el máximo") Long valorCop) {}

    public record FirmaSubida(
            @NotNull(message = "solicitudId es obligatorio") @Min(value = 1, message = "solicitudId no es válido") Long solicitudId) {}

    public record FotoNube(
            @NotBlank(message = "publicId es obligatorio") @Size(max = 200, message = "publicId es demasiado largo") String publicId,
            @NotBlank(message = "version es obligatoria") @Pattern(regexp = "\\d{1,12}", message = "version no es válida") String version,
            @NotBlank(message = "signature es obligatoria") @Size(max = 128, message = "signature es demasiado larga") String signature,
            @Size(max = 10, message = "format es demasiado largo") String format) {}

    public record ZonaTrabajo(
            @NotNull(message = "lat es obligatoria") @DecimalMin(value = "-90", message = "lat fuera de rango") @DecimalMax(value = "90", message = "lat fuera de rango") Double lat,
            @NotNull(message = "lng es obligatoria") @DecimalMin(value = "-180", message = "lng fuera de rango") @DecimalMax(value = "180", message = "lng fuera de rango") Double lng,
            @Min(value = 1, message = "el radio mínimo es 1 km") @Max(value = 50, message = "el radio máximo es 50 km") Integer radioKm) {}

    public record MensajeCrear(
            @NotBlank(message = "el mensaje no puede estar vacío") @Size(max = 500, message = "el mensaje admite hasta 500 caracteres") String texto) {}

    public record CalificacionCrear(
            @NotNull(message = "las estrellas son obligatorias") @Min(value = 1, message = "las estrellas van de 1 a 5") @Max(value = 5, message = "las estrellas van de 1 a 5") Integer estrellas,
            @Size(max = 300, message = "el comentario admite hasta 300 caracteres") String comentario) {}

    public record EstadoCuenta(
            @NotNull(message = "status es obligatorio") @Pattern(regexp = "ACTIVE|SUSPENDED", message = "status debe ser ACTIVE o SUSPENDED") String status,
            String reason) {}

    public record DecisionVerificacion(
            @NotNull(message = "decision es obligatoria") @Pattern(regexp = "APPROVED|REJECTED", message = "decision debe ser APPROVED o REJECTED") String decision,
            @Size(max = 200, message = "motivo admite hasta 200 caracteres") String motivo) {}

    public record MotivoAdmin(
            @NotBlank(message = "el motivo es obligatorio") @Size(min = 5, max = 200, message = "el motivo debe tener entre 5 y 200 caracteres") String motivo) {}
}
