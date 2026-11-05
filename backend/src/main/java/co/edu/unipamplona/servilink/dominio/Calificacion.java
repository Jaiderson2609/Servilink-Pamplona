package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "calificaciones")
public class Calificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(name = "autor_id")
    private Long autorId;
    @Column(name = "destinatario_id")
    private Long destinatarioId;
    private Integer estrellas;
    private String comentario;
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected Calificacion() {}

    public Calificacion(Long solicitudId, Long autorId, Long destinatarioId, Integer estrellas, String comentario,
                        Instant creadaEn) {
        this.solicitudId = solicitudId;
        this.autorId = autorId;
        this.destinatarioId = destinatarioId;
        this.estrellas = estrellas;
        this.comentario = comentario;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getAutorId() { return autorId; }
    public Long getDestinatarioId() { return destinatarioId; }
    public Integer getEstrellas() { return estrellas; }
    public String getComentario() { return comentario; }
    public Instant getCreadaEn() { return creadaEn; }
}
