package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "postulaciones")
public class Postulacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    private Integer precio;
    private String mensaje;
    private String estado = "ACTIVA";
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected Postulacion() {}

    public Postulacion(Long solicitudId, Long tecnicoId, Integer precio, String mensaje, Instant creadaEn) {
        this.solicitudId = solicitudId;
        this.tecnicoId = tecnicoId;
        this.precio = precio;
        this.mensaje = mensaje;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getTecnicoId() { return tecnicoId; }
    public Integer getPrecio() { return precio; }
    public String getMensaje() { return mensaje; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
