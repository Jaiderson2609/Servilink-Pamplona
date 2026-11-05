package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fotos_solicitud")
public class FotoSolicitud {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(name = "subida_por")
    private Long subidaPor;
    @Column(name = "tipo_mime")
    private String tipoMime;
    @Column(name = "tamano_bytes")
    private Integer tamanoBytes;
    private byte[] contenido;
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected FotoSolicitud() {}

    public FotoSolicitud(Long solicitudId, Long subidaPor, String tipoMime, byte[] contenido, Instant creadaEn) {
        this.solicitudId = solicitudId;
        this.subidaPor = subidaPor;
        this.tipoMime = tipoMime;
        this.tamanoBytes = contenido.length;
        this.contenido = contenido;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public String getTipoMime() { return tipoMime; }
    public Integer getTamanoBytes() { return tamanoBytes; }
    public byte[] getContenido() { return contenido; }
    public Instant getCreadaEn() { return creadaEn; }
}
