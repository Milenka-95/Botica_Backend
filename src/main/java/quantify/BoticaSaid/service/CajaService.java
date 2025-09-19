package quantify.BoticaSaid.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quantify.BoticaSaid.dto.*;
import quantify.BoticaSaid.model.*;
import quantify.BoticaSaid.repository.BoletaRepository;
import quantify.BoticaSaid.repository.CajaRepository;
import quantify.BoticaSaid.repository.MovimientoEfectivoRepository;
import quantify.BoticaSaid.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class CajaService {

    private final CajaRepository cajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoEfectivoRepository movimientoEfectivoRepository;
    private final BoletaRepository boletaRepository;

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    public CajaService(
            CajaRepository cajaRepository,
            UsuarioRepository usuarioRepository,
            MovimientoEfectivoRepository movimientoEfectivoRepository,
            BoletaRepository boletaRepository
    ) {
        this.cajaRepository = cajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoEfectivoRepository = movimientoEfectivoRepository;
        this.boletaRepository = boletaRepository;
    }

    /* ===================== Apertura / Cierre ===================== */

    @Transactional
    public Caja abrirCaja(CajaAperturaDTO dto) {
        Usuario usuario = usuarioRepository.findByDni(dto.getDniUsuario())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con DNI: " + dto.getDniUsuario()));

        if (cajaRepository.existsByUsuarioAndFechaCierreIsNull(usuario)) {
            throw new RuntimeException("Ya existe una caja abierta para este usuario.");
        }

        Caja caja = new Caja();
        caja.setUsuario(usuario);
        caja.setFechaApertura(LocalDateTime.now(ZONE));
        caja.setEfectivoInicial(dto.getEfectivoInicial());
        caja.setEfectivoFinal(null);
        caja.setTotalYape(null);
        caja.setDiferencia(null);

        cajaRepository.save(caja);
        return caja;
    }

    @Transactional
    public Caja cerrarCaja(CierreCajaDTO cierreCajaDTO) {
        Usuario usuario = usuarioRepository.findByDni(cierreCajaDTO.getDniUsuario())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con DNI: " + cierreCajaDTO.getDniUsuario()));

        Caja caja = cajaRepository.findByUsuarioAndFechaCierreIsNull(usuario)
                .orElseThrow(() -> new RuntimeException("No hay caja abierta para el usuario."));

        List<MovimientoEfectivo> movimientos = movimientoEfectivoRepository.findByCajaAndEsManual(caja, true);

        BigDecimal ingresos = movimientos.stream()
                .filter(m -> m.getTipo() == MovimientoEfectivo.TipoMovimiento.INGRESO)
                .map(MovimientoEfectivo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal egresos = movimientos.stream()
                .filter(m -> m.getTipo() == MovimientoEfectivo.TipoMovimiento.EGRESO)
                .map(MovimientoEfectivo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime desde = caja.getFechaApertura();
        LocalDateTime hasta = LocalDateTime.now(ZONE);

        List<Boleta> boletas = boletaRepository.findByUsuarioAndFechaVentaBetween(usuario, desde, hasta);

        BigDecimal ventasEfectivo = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.EFECTIVO)
                .map(Boleta::getTotalCompra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasMixtoEfectivo = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.MIXTO)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getEfectivo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasMixtoDigital = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.MIXTO)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getDigital()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasYape = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.YAPE)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getDigital()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVentasEfectivo = ventasEfectivo.add(ventasMixtoEfectivo);
        BigDecimal totalVentasDigital = ventasYape.add(ventasMixtoDigital);
        BigDecimal totalVentas = totalVentasEfectivo.add(totalVentasDigital);

        BigDecimal totalEfectivoEsperado = safe(caja.getEfectivoInicial())
                .add(ingresos)
                .add(totalVentasEfectivo)
                .subtract(egresos);

        BigDecimal efectivoFinal = safe(cierreCajaDTO.getEfectivoFinalDeclarado());
        BigDecimal diferencia = efectivoFinal.subtract(totalEfectivoEsperado);

        caja.setFechaCierre(hasta);
        caja.setEfectivoFinal(efectivoFinal);
        caja.setTotalYape(totalVentasDigital); // yape + parte digital mixto
        caja.setDiferencia(diferencia);

        return cajaRepository.save(caja);
    }

    /* ===================== Consultas básicas ===================== */

    public Caja obtenerCajaAbiertaPorUsuario(String dniUsuario) {
        Usuario usuario = usuarioRepository.findByDni(dniUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con DNI: " + dniUsuario));
        return cajaRepository.findByUsuarioAndFechaCierreIsNull(usuario)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CajaResumenDTO> obtenerHistorialCajas() {
        // Orden descendente si tienes el método en repositorio
        List<Caja> cajas = cajaRepository.findAllByOrderByFechaAperturaDesc();
        return cajas.stream()
                .map(this::convertirCajaAResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CajaResumenDTO> obtenerHistorialCajasConMovimientos(boolean soloManuales) {
        List<Caja> cajas = cajaRepository.findAllByOrderByFechaAperturaDesc();
        return cajas.stream()
                .map(caja -> {
                    CajaResumenDTO dto = convertirCajaAResumen(caja); // ya trae movimientos manuales
                    if (!soloManuales) {
                        // Reemplaza lista con todos los movimientos
                        var todos = movimientoEfectivoRepository.findByCaja(caja);
                        var movDtos = todos.stream()
                                .sorted(Comparator.comparing(MovimientoEfectivo::getFecha))
                                .map(this::mapMovimientoBasico)
                                .toList();
                        dto.setMovimientos(movDtos);
                    }
                    // asegurar id
                    if (dto.getId() == null) dto.setId(caja.getId());
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CajaResumenDTO> obtenerCajasAbiertas() {
        List<Caja> abiertas = cajaRepository.findByFechaCierreIsNull();
        return abiertas.stream()
                .map(this::convertirCajaAResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public CajaResumenDTO obtenerResumenCajaActual(String dniUsuario) {
        Usuario usuario = usuarioRepository.findByDni(dniUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con DNI: " + dniUsuario));

        Caja caja = cajaRepository.findByUsuarioAndFechaCierreIsNull(usuario).orElse(null);
        if (caja == null) return null;
        return convertirCajaAResumen(caja);
    }

    /* ===================== Movimientos ===================== */

    // MÉTODO QUE TE FALTA (firma exacta que invoca el controller)
    @Transactional
    public void registrarMovimientoManual(MovimientoEfectivoDTO dto) {
        Usuario usuario = usuarioRepository.findByDni(dto.getDniUsuario())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con DNI: " + dto.getDniUsuario()));

        Caja caja = cajaRepository.findByUsuarioAndFechaCierreIsNull(usuario)
                .orElseThrow(() -> new RuntimeException("No hay caja abierta para el usuario."));

        MovimientoEfectivo mov = new MovimientoEfectivo();
        mov.setCaja(caja);
        mov.setTipo(MovimientoEfectivo.TipoMovimiento.valueOf(dto.getTipo().toUpperCase())); // normaliza
        mov.setMonto(dto.getMonto()); // <-- sin valueOf
        mov.setDescripcion(dto.getDescripcion());
        mov.setFecha(LocalDateTime.now(ZoneId.of("America/Lima")));
        mov.setUsuario(usuario);
        mov.setEsManual(true);
        movimientoEfectivoRepository.save(mov);
    }

    // (Opcional) Variante que devuelve el DTO creado
    @Transactional
    public MovimientoDTO registrarMovimientoManualYDevolver(MovimientoEfectivoDTO dto) {
        registrarMovimientoManual(dto);
        // Recuperar último manual registrado (simple, podrías optimizar)
        Usuario usuario = usuarioRepository.findByDni(dto.getDniUsuario()).orElseThrow();
        Caja caja = cajaRepository.findByUsuarioAndFechaCierreIsNull(usuario).orElse(null);
        if (caja == null) return null;
        return movimientoEfectivoRepository.findByCajaAndEsManual(caja, true).stream()
                .max(Comparator.comparing(MovimientoEfectivo::getFecha))
                .map(this::mapMovimientoBasico)
                .orElse(null);
    }

    /* ===================== Conversión / Cálculo ===================== */

    public CajaResumenDTO convertirCajaAResumen(Caja caja) {
        List<MovimientoEfectivo> movimientosManual = movimientoEfectivoRepository.findByCajaAndEsManual(caja, true);

        BigDecimal ingresos = movimientosManual.stream()
                .filter(m -> m.getTipo() == MovimientoEfectivo.TipoMovimiento.INGRESO)
                .map(MovimientoEfectivo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal egresos = movimientosManual.stream()
                .filter(m -> m.getTipo() == MovimientoEfectivo.TipoMovimiento.EGRESO)
                .map(MovimientoEfectivo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime desde = caja.getFechaApertura();
        LocalDateTime hasta = caja.getFechaCierre() != null ? caja.getFechaCierre() : LocalDateTime.now(ZONE);

        List<Boleta> boletas = boletaRepository.findByUsuarioAndFechaVentaBetween(
                caja.getUsuario(), desde, hasta);

        BigDecimal ventasEfectivo = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.EFECTIVO)
                .map(Boleta::getTotalCompra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasMixtoEfectivo = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.MIXTO)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getEfectivo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasMixtoDigital = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.MIXTO)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getDigital()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ventasYape = boletas.stream()
                .filter(b -> b.getMetodoPago() != null && b.getMetodoPago().getNombre() == MetodoPago.NombreMetodo.YAPE)
                .map(b -> BigDecimal.valueOf(b.getMetodoPago().getDigital()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVentasEfectivo = ventasEfectivo.add(ventasMixtoEfectivo);
        BigDecimal totalVentasDigital = ventasYape.add(ventasMixtoDigital);
        BigDecimal totalVentas = totalVentasEfectivo.add(totalVentasDigital);

        BigDecimal efectivo = safe(caja.getEfectivoInicial())
                .add(ingresos)
                .add(totalVentasEfectivo)
                .subtract(egresos);

        BigDecimal totalYape = ventasYape.add(ventasMixtoDigital);

        List<MovimientoDTO> movimientosDTO = movimientosManual.stream()
                .map(this::mapMovimientoBasico)
                .toList();

        CajaResumenDTO dto = new CajaResumenDTO();
        dto.setId(caja.getId());
        dto.setEfectivoInicial(caja.getEfectivoInicial());
        dto.setEfectivoFinal(caja.getEfectivoFinal());
        dto.setIngresos(ingresos);
        dto.setEgresos(egresos);
        dto.setVentasEfectivo(totalVentasEfectivo);
        dto.setVentasYape(ventasYape);

        dto.setVentasPlin(BigDecimal.ZERO);
        dto.setVentasMixto(ventasMixtoDigital);
        dto.setTotalVentas(totalVentas);
        dto.setEfectivo(efectivo);
        dto.setTotalYape(totalYape != null ? totalYape : BigDecimal.ZERO);
        dto.setMovimientos(movimientosDTO);
        dto.setDiferencia(caja.getDiferencia());
        dto.setCajaAbierta(caja.getFechaCierre() == null);
        dto.setFechaApertura(caja.getFechaApertura() != null ? caja.getFechaApertura().toString() : null);
        dto.setFechaCierre(caja.getFechaCierre() != null ? caja.getFechaCierre().toString() : null);
        dto.setUsuarioResponsable(
                caja.getUsuario() != null ? caja.getUsuario().getNombreCompleto() : null
        );
        return dto;
    }

    private MovimientoDTO mapMovimientoBasico(MovimientoEfectivo m) {
        MovimientoDTO dto = new MovimientoDTO();
        dto.setId(m.getId().longValue());
        dto.setFecha(m.getFecha().toString());
        dto.setTipo(m.getTipo().toString().toLowerCase());
        dto.setDescripcion(m.getDescripcion());
        dto.setMonto(m.getMonto());
        dto.setUsuario(m.getUsuario().getNombreCompleto());
        return dto;
    }

    /* ===================== Dashboard ===================== */

    @Transactional(readOnly = true)
    public DashboardResumenDTO.SaldoCajaDTO getSaldoActual() {
        Caja caja = cajaRepository.findFirstByFechaCierreIsNullOrderByFechaAperturaDesc()
                .orElse(null);

        DashboardResumenDTO.SaldoCajaDTO dto = new DashboardResumenDTO.SaldoCajaDTO();
        if (caja == null) {
            dto.total = 0.0;
            dto.efectivo = 0.0;
            dto.yape = 0.0;
            return dto;
        }
        BigDecimal efectivo = caja.getEfectivoFinal() != null ? caja.getEfectivoFinal() : BigDecimal.ZERO;
        BigDecimal yape = caja.getTotalYape() != null ? caja.getTotalYape() : BigDecimal.ZERO;
        dto.efectivo = efectivo.doubleValue();
        dto.yape = yape.doubleValue();
        dto.total = efectivo.add(yape).doubleValue();
        return dto;
    }

    /* ===================== Util ===================== */
    private static BigDecimal safe(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    @Transactional(readOnly = true)
    public List<MovimientoDTO> obtenerMovimientosCaja(Integer cajaId, boolean soloManuales, String direction) {
        Caja caja = cajaRepository.findById(Long.valueOf(cajaId))
                .orElseThrow(() -> new RuntimeException("Caja no encontrada con id: " + cajaId));

        List<MovimientoEfectivo> movimientos;

        if (soloManuales) {
            movimientos = movimientoEfectivoRepository.findByCajaAndEsManualOrderByFechaAsc(caja, true);
        } else {
            movimientos = movimientoEfectivoRepository.findByCajaOrderByFechaAsc(caja);
        }

        if ("DESC".equalsIgnoreCase(direction)) {
            movimientos = movimientos.stream()
                    .sorted((a,b) -> b.getFecha().compareTo(a.getFecha()))
                    .toList();
        }

        return movimientos.stream()
                .map(this::mapMovimientoBasico)
                .toList();
    }
}