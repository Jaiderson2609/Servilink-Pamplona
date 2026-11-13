package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

/** Intento de recarga con Wompi. PENDING hasta que llega el evento firmado; APPROVED es definitivo. */
@Entity
@Table(name = "recargas")
public class Recarga {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String FAILED = "FAILED";
    public static final String REVIEW = "REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    private String referencia;
    private long valor;
    private String estado = PENDING;
    @Column(name = "transaccion_wompi")
    private String transaccionWompi;
    @Column(name = "creada_en")
    private Instant creadaEn;
    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    protected Recarga() {}

    public Recarga(Long tecnicoId, String referencia, long valor, Instant creadaEn) {
        this.tecnicoId = tecnicoId;
        this.referencia = referencia;
        this.valor = valor;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getTecnicoId() { return tecnicoId; }
    public String getReferencia() { return referencia; }
    public long getValor() { return valor; }
    public String getEstado() { return estado; }
    public String getTransaccionWompi() { return transaccionWompi; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getResueltaEn() { return resueltaEn; }

    /** Un pago aprobado o en revisión no vuelve a cambiar; uno rechazado sí puede aprobarse en un reintento del cliente. */
    public boolean admiteResultado() {
        return PENDING.equals(estado) || FAILED.equals(estado);
    }

    public void resolver(String nuevoEstado, String transaccion, Instant cuando) {
        this.estado = nuevoEstado;
        this.transaccionWompi = transaccion;
        this.resueltaEn = cuando;
    }
}
