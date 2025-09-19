package quantify.BoticaSaid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import quantify.BoticaSaid.model.MovimientoEfectivo;
import quantify.BoticaSaid.model.Caja;

import java.util.List;

@Repository
public interface MovimientoEfectivoRepository extends JpaRepository<MovimientoEfectivo, Integer> {

    List<MovimientoEfectivo> findByCaja(Caja caja);

    List<MovimientoEfectivo> findByCajaAndEsManual(Caja caja, boolean esManual);

    // NUEVOS (orden ascendiente por fecha)
    List<MovimientoEfectivo> findByCajaOrderByFechaAsc(Caja caja);

    List<MovimientoEfectivo> findByCajaAndEsManualOrderByFechaAsc(Caja caja, boolean esManual);
}