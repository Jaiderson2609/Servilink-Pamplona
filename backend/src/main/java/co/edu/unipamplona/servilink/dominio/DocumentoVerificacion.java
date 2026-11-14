package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

/** Imagen privada que el técnico envía para verificar su identidad. Se borra cuando el administrador decide. */
@Entity
@Table(name = "documentos_verificacion")
public class DocumentoVerificacion {
    public static final java.util.List<String> TIPOS = java.util.List.of("CEDULA_FRENTE", "CEDULA_REVERSO", "SELFIE");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    private String tipo;
    @Column(name = "tipo_mime")
    private String tipoMime;
    private byte[] contenido;
    @Column(name = "creado_en")
    private Instant creadoEn;

    protected DocumentoVerificacion() {}

    public DocumentoVerificacion(Long tecnicoId, String tipo, String tipoMime, byte[] contenido, Instant creadoEn) {
        this.tecnicoId = tecnicoId;
        this.tipo = tipo;
        this.tipoMime = tipoMime;
        this.contenido = contenido;
        this.creadoEn = creadoEn;
    }

    public Long getTecnicoId() { return tecnicoId; }
    public String getTipo() { return tipo; }
    public String getTipoMime() { return tipoMime; }
    public byte[] getContenido() { return contenido; }
}
