package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;
import java.time.Instant;

/** Zona de trabajo del técnico. La columna "punto" la calcula PostgreSQL y no se mapea aquí. */
@Entity
@Table(name = "ubicaciones_tecnico")
public class UbicacionTecnico {
    @Id
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    private Double lat;
    private Double lng;
    @Column(name = "radio_km")
    private Integer radioKm;
    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    protected UbicacionTecnico() {}

    public UbicacionTecnico(Long tecnicoId, Double lat, Double lng, Integer radioKm, Instant actualizadaEn) {
        this.tecnicoId = tecnicoId;
        this.lat = lat;
        this.lng = lng;
        this.radioKm = radioKm;
        this.actualizadaEn = actualizadaEn;
    }

    public void actualizar(Double lat, Double lng, Integer radioKm, Instant ahora) {
        this.lat = lat;
        this.lng = lng;
        this.radioKm = radioKm;
        this.actualizadaEn = ahora;
    }

    public Long getTecnicoId() { return tecnicoId; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Integer getRadioKm() { return radioKm; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
