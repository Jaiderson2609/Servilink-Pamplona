package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Movimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepositorio extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findTop20ByTecnicoIdOrderByIdDesc(Long tecnicoId);

    List<Movimiento> findBySolicitudIdOrderByIdAsc(Long solicitudId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(m.valor), 0) from Movimiento m where m.tipo = 'COMISION'")
    long comisionesCobradas();
}
