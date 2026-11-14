package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String rol;
    private String nombre;
    private String correo;
    private String telefono;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "token_push")
    private String tokenPush;

    private String disponibilidad = "AVAILABLE";
    private String verificacion = "SIN_ENVIAR";
    @Column(name = "motivo_verificacion")
    private String motivoVerificacion;
    @Column(name = "verificacion_enviada_en")
    private java.time.Instant verificacionEnviadaEn;

    private boolean suspendido;
    @Column(name = "motivo_suspension")
    private String motivoSuspension;
    @Column(name = "suspendido_en")
    private java.time.Instant suspendidoEn;

    protected Usuario() {}

    public Usuario(String rol, String nombre, String correo, String telefono, String passwordHash) {
        this.rol = rol;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getRol() { return rol; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getPasswordHash() { return passwordHash; }
    public String getTokenPush() { return tokenPush; }
    public void setTokenPush(String tokenPush) { this.tokenPush = tokenPush; }
    public String getDisponibilidad() { return disponibilidad; }
    public void setDisponibilidad(String disponibilidad) { this.disponibilidad = disponibilidad; }
    public String getVerificacion() { return verificacion; }
    public String getMotivoVerificacion() { return motivoVerificacion; }
    public java.time.Instant getVerificacionEnviadaEn() { return verificacionEnviadaEn; }

    public void enviarVerificacion(java.time.Instant ahora) {
        this.verificacion = "PENDIENTE";
        this.motivoVerificacion = null;
        this.verificacionEnviadaEn = ahora;
    }

    public void resolverVerificacion(boolean aprobada, String motivo) {
        this.verificacion = aprobada ? "APROBADA" : "RECHAZADA";
        this.motivoVerificacion = aprobada ? null : motivo;
    }

    public boolean isSuspendido() { return suspendido; }
    public String getMotivoSuspension() { return motivoSuspension; }

    public void suspender(String motivo, java.time.Instant ahora) {
        this.suspendido = true;
        this.motivoSuspension = motivo;
        this.suspendidoEn = ahora;
    }

    public void reactivar() {
        this.suspendido = false;
        this.motivoSuspension = null;
        this.suspendidoEn = null;
    }
}
