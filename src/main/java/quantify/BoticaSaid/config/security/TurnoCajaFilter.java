package quantify.BoticaSaid.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import quantify.BoticaSaid.model.Usuario;
import quantify.BoticaSaid.repository.UsuarioRepository;
import quantify.BoticaSaid.repository.CajaRepository;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class TurnoCajaFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarioRepository;
    private final CajaRepository cajaRepository;

    public TurnoCajaFilter(UsuarioRepository usuarioRepository, CajaRepository cajaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cajaRepository = cajaRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            Usuario usuario = usuarioRepository.findByDni(userDetails.getUsername()).orElse(null);
            if (usuario != null && usuario.getRol().toString().equalsIgnoreCase("TRABAJADOR")) {
                LocalTime ahora = LocalTime.now(ZoneId.of("America/Lima"));
                LocalTime entrada = usuario.getHorarioEntrada();
                LocalTime salida = usuario.getHorarioSalida();
                boolean turnoEsOvernight = entrada.isAfter(salida);
                boolean fueraDeHorario = (!turnoEsOvernight && (ahora.isBefore(entrada) || ahora.isAfter(salida)))
                        || (turnoEsOvernight && (ahora.isAfter(salida) && ahora.isBefore(entrada)));

                if (fueraDeHorario) {
                    String uri = request.getRequestURI();
                    String metodo = request.getMethod();
                    System.out.println("TurnoCajaFilter URI: " + uri + " | método: " + metodo);

                    boolean esRutaPermitida =
                            uri.equals("/usuarios/me") ||
                                    uri.contains("/api/cajas/actual") ||
                                    uri.contains("/api/cajas/historial") ||
                                    uri.contains("/api/cajas/abiertas") ||
                                    (uri.equals("/api/cajas/cerrar") && metodo.equals("POST")) ||
                                    uri.contains("/api/cajas/movimiento");

                    // Si es una ruta permitida, deja pasar SIEMPRE
                    if (esRutaPermitida) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Si NO es ruta permitida, aplica la lógica de cierre de caja
                    boolean tieneCajaAbierta = cajaRepository.existsByUsuarioAndFechaCierreIsNull(usuario);

                    if (!tieneCajaAbierta) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("""
                        {
                            "message": "Fuera de horario laboral. No puedes ingresar fuera de tu horario laboral."
                        }
                        """);
                        return;
                    } else {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("""
                        {
                            "message": "Fuera de horario laboral. Debes cerrar tu caja antes de salir. Solo puedes acceder a la función de cierre de caja."
                        }
                        """);
                        return;
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}