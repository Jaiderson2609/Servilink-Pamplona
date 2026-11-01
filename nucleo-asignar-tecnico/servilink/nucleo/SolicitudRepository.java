package servilink.nucleo;

import java.util.Optional;

/** Puerto de salida: cómo el núcleo obtiene y guarda solicitudes. */
public interface SolicitudRepository {
    Optional<Solicitud> buscar(String id);
    void guardar(Solicitud solicitud);
}
