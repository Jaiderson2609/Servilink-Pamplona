package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auditoria_admin")
public class AuditoriaAdmin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "admin_id")
    private Long adminId;
    private String accion;
    @Column(name = "objetivo_tipo")
    private String objetivoTipo;
    @Column(name = "objetivo_id")
    private Long objetivoId;
    private String detalle;
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected AuditoriaAdmin() {}

    public AuditoriaAdmin(Long adminId, String accion, String objetivoTipo, Long objetivoId, String detalle, Instant creadaEn) {
        this.adminId = adminId;
        this.accion = accion;
        this.objetivoTipo = objetivoTipo;
        this.objetivoId = objetivoId;
        this.detalle = detalle;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public String getAccion() { return accion; }
    public String getObjetivoTipo() { return objetivoTipo; }
    public Long getObjetivoId() { return objetivoId; }
    public String getDetalle() { return detalle; }
    public Instant getCreadaEn() { return creadaEn; }
}
