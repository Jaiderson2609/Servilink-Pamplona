package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.UbicacionTecnico;
import co.edu.unipamplona.servilink.repositorio.UbicacionTecnicoRepositorio;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Zona de trabajo del técnico (RF-018): punto base y radio de desplazamiento. */
@Service
public class ZonaTrabajoService {

    public static final int RADIO_POR_DEFECTO_KM = 5;

    private final UbicacionTecnicoRepositorio ubicaciones;
    private final Clock reloj;

    public ZonaTrabajoService(UbicacionTecnicoRepositorio ubicaciones, Clock reloj) {
        this.ubicaciones = ubicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public UbicacionTecnico guardar(Long tecnicoId, double lat, double lng, Integer radioKm) {
        int radio = radioKm == null ? RADIO_POR_DEFECTO_KM : radioKm;
        var existente = ubicaciones.findById(tecnicoId);
        if (existente.isPresent()) {
            existente.get().actualizar(lat, lng, radio, reloj.instant());
            return existente.get();
        }
        return ubicaciones.save(new UbicacionTecnico(tecnicoId, lat, lng, radio, reloj.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<UbicacionTecnico> consultar(Long tecnicoId) {
        return ubicaciones.findById(tecnicoId);
    }
}
