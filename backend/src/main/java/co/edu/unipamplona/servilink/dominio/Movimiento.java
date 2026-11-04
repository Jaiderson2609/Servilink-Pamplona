package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

/** Asiento del libro contable: solo se inserta, nunca se modifica (lo garantiza un disparador en la base). */
@Entity
@Table(name = "libro_contable")
public class Movimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    private String tipo;
    private long valor;
    @Column(name = "saldo_posterior")
    private long saldoPosterior;
    private String referencia;
    @Column(name = "creado_en")
    private Instant creadoEn;

    protected Movimiento() {}

    public Movimiento(Long tecnicoId, Long solicitudId, String tipo, long valor, long saldoPosterior,
                      String referencia, Instant creadoEn) {
        this.tecnicoId = tecnicoId;
        this.solicitudId = solicitudId;
        this.tipo = tipo;
        this.valor = valor;
        this.saldoPosterior = saldoPosterior;
        this.referencia = referencia;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getTecnicoId() { return tecnicoId; }
    public Long getSolicitudId() { return solicitudId; }
    public String getTipo() { return tipo; }
    public long getValor() { return valor; }
    public long getSaldoPosterior() { return saldoPosterior; }
    public String getReferencia() { return referencia; }
    public Instant getCreadoEn() { return creadoEn; }
}
