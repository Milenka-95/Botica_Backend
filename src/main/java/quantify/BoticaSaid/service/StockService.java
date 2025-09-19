package quantify.BoticaSaid.service;

import quantify.BoticaSaid.dto.StockItemDTO;
import quantify.BoticaSaid.model.Stock;
import quantify.BoticaSaid.model.Producto;
import quantify.BoticaSaid.repository.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

    public List<StockItemDTO> listarStock() {
        List<Stock> stocks = stockRepository.findAllWithProducto();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return stocks.stream().map(stock -> {
            Producto producto = stock.getProducto();
            String fechaIso = stock.getFechaVencimiento() != null
                    ? stock.getFechaVencimiento().format(formatter)
                    : null;
            return new StockItemDTO(
                    stock.getId(),
                    stock.getCodigoStock(),
                    producto.getCodigoBarras(),
                    producto.getNombre(),
                    producto.getConcentracion(),
                    stock.getCantidadUnidades(),
                    producto.getCantidadMinima() != null ? producto.getCantidadMinima() : 0,
                    stock.getPrecioCompra(),
                    producto.getPrecioVentaUnd(),
                    fechaIso,
                    producto.getLaboratorio(),
                    producto.getCategoria()
            );
        }).collect(Collectors.toList());
    }

    public void actualizarStock(int id, StockItemDTO dto) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock no encontrado"));
        stock.setCodigoStock(dto.getCodigoStock()); // <--- ¡IMPORTANTE! Ahora sí se actualiza el código de lote
        stock.setCantidadUnidades(dto.getCantidadUnidades());
        stock.setPrecioCompra(dto.getPrecioCompra());

        try {
            if (dto.getFechaVencimiento() != null) {
                LocalDate fecha = LocalDate.parse(dto.getFechaVencimiento(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                stock.setFechaVencimiento(fecha);
            }
        } catch (Exception e) {
            stock.setFechaVencimiento(null);
        }
        stockRepository.save(stock);
    }
}