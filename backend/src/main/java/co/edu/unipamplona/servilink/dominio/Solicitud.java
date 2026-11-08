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
    @Column(name = "oficio_id")
    private Integer oficioId;
    private Double lat;
    private Double lng;
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
    @Column(name = "terminado_en")
    private Instant terminadoEn;
    @Column(name = "cancelada_por")
    private String canceladaPor;
    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    protected Solicitud() {}

    public Solicitud(Long hogarId, Integer oficioId, String descripcion, Double lat, Double lng, Instant expiraEn,
                     Instant creadaEn) {
        this.hogarId = hogarId;
        this.oficioId = oficioId;
        this.lat = lat;
        this.lng = lng;
        this.descripcion = descripcion;
        this.estado = EstadoSolicitud.E01.codigo();
        this.expiraEn = expiraEn;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getHogarId() { return hogarId; }
    public Integer getOficioId() { return oficioId; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
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
    public Instant getTerminadoEn() { return terminadoEn; }
    public void setTerminadoEn(Instant terminadoEn) { this.terminadoEn = terminadoEn; }
    public String getCanceladaPor() { return canceladaPor; }
    public void setCanceladaPor(String canceladaPor) { this.canceladaPor = canceladaPor; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
}
