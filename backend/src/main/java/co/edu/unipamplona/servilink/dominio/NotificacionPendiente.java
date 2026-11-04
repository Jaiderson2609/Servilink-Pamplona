package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notificaciones_pendientes")
public class NotificacionPendiente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String clave;
    @Column(name = "destinatario_id")
    private Long destinatarioId;
    private String texto;
    private String estado = "PENDIENTE";
    private int intentos;
    @Column(name = "creada_en")
    private Instant creadaEn;

    protected NotificacionPendiente() {}

    public NotificacionPendiente(String clave, Long destinatarioId, String texto, Instant creadaEn) {
        this.clave = clave;
        this.destinatarioId = destinatarioId;
        this.texto = texto;
        this.creadaEn = creadaEn;
    }

    public Long getId() { return id; }
    public String getClave() { return clave; }
    public Long getDestinatarioId() { return destinatarioId; }
    public String getTexto() { return texto; }
    public String getEstado() { return estado; }
    public int getIntentos() { return intentos; }

    public void registrarIntento(boolean entregado) {
        intentos++;
        if (entregado) {
            estado = "ENVIADA";
        }
    }
}
