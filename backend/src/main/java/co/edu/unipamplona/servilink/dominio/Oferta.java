package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

/** Propuesta de precio dentro de una postulación: la inicial y la final del técnico, y la contraoferta del hogar. */
@Entity
@Table(name = "ofertas")
public class Oferta {
    public static final String TECNICO = "TECNICO";
    public static final String HOGAR = "HOGAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "postulacion_id")
    private Long postulacionId;
    private String autor;
    private Integer monto;
    private Integer secuencia;
    private String estado = "PENDIENTE";
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected Oferta() {}

    public Oferta(Long postulacionId, String autor, Integer monto, Integer secuencia, Instant creadaEn) {
        this.postulacionId = postulacionId;
        this.autor = autor;
        this.monto = monto;
        this.secuencia = secuencia;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getPostulacionId() { return postulacionId; }
    public String getAutor() { return autor; }
    public Integer getMonto() { return monto; }
    public Integer getSecuencia() { return secuencia; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getCreadaEn() { return creadaEn; }
}
