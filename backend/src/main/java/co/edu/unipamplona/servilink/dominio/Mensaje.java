package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mensajes")
public class Mensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(name = "autor_id")
    private Long autorId;
    private String texto;
    @Column(name = "creado_en")
    private Instant creadoEn;

    protected Mensaje() {}

    public Mensaje(Long solicitudId, Long autorId, String texto, Instant creadoEn) {
        this.solicitudId = solicitudId;
        this.autorId = autorId;
        this.texto = texto;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getAutorId() { return autorId; }
    public String getTexto() { return texto; }
    public Instant getCreadoEn() { return creadoEn; }
}
