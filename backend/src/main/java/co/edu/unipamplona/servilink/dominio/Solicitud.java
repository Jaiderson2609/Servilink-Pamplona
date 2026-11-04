package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "solicitudes")
public class Solicitud {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "hogar_id")
    private Long hogarId;
    private String oficio;
    private String descripcion;
    private String estado;
    @Column(name = "expira_en")
    private Instant expiraEn;
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    @Column(name = "precio_acordado")
    private Integer precioAcordado;
    @Column(name = "comision_pct")
    private BigDecimal comisionPct;
    @Column(name = "comision_cop")
    private Integer comisionCop;
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected Solicitud() {}

    public Solicitud(Long hogarId, String oficio, String descripcion, Instant expiraEn, Instant creadaEn) {
        this.hogarId = hogarId;
        this.oficio = oficio;
        this.descripcion = descripcion;
        this.estado = "E01";
        this.expiraEn = expiraEn;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getHogarId() { return hogarId; }
    public String getOficio() { return oficio; }
    public String getDescripcion() { return descripcion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
    public Long getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(Long tecnicoId) { this.tecnicoId = tecnicoId; }
    public Integer getPrecioAcordado() { return precioAcordado; }
    public void setPrecioAcordado(Integer precioAcordado) { this.precioAcordado = precioAcordado; }
    public BigDecimal getComisionPct() { return comisionPct; }
    public void setComisionPct(BigDecimal comisionPct) { this.comisionPct = comisionPct; }
    public Integer getComisionCop() { return comisionCop; }
    public void setComisionCop(Integer comisionCop) { this.comisionCop = comisionCop; }
    public Instant getCreadaEn() { return creadaEn; }
}
