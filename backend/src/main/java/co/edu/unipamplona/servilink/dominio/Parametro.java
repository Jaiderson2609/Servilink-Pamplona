package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "parametros")
public class Parametro {
    @Id
    private String clave;
    private BigDecimal valor;
    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    protected Parametro() {}

    public Parametro(String clave, BigDecimal valor, Instant actualizadoEn, Long actualizadoPor) {
        this.clave = clave;
        this.valor = valor;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    public void cambiar(BigDecimal valor, Instant ahora, Long adminId) {
        this.valor = valor;
        this.actualizadoEn = ahora;
        this.actualizadoPor = adminId;
    }

    public String getClave() { return clave; }
    public BigDecimal getValor() { return valor; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
