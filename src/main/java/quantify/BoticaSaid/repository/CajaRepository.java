package quantify.BoticaSaid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import quantify.BoticaSaid.model.Caja;
import quantify.BoticaSaid.model.Usuario;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {

    boolean existsByUsuarioAndFechaCierreIsNull(Usuario usuario);


    Optional<Caja> findByUsuarioAndFechaCierreIsNull(Usuario usuario);

    @Query("SELECT c FROM Caja c WHERE c.usuario.dni = :dni AND c.fechaCierre IS NULL")
    Optional<Caja> findCajaAbiertaPorDniUsuario(@Param("dni") String dni);
    List<Caja> findByFechaCierreIsNull();

    Optional<Caja> findFirstByFechaCierreIsNullOrderByFechaAperturaDesc();

    // NUEVO: para historial ordenado (más reciente primero)
    List<Caja> findAllByOrderByFechaAperturaDesc();
}

