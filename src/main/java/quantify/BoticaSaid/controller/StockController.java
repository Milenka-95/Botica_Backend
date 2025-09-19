package quantify.BoticaSaid.controller;

import quantify.BoticaSaid.dto.ProductSummaryDTO;
import quantify.BoticaSaid.dto.StockItemDTO;
import quantify.BoticaSaid.service.StockService;
import quantify.BoticaSaid.service.StockSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockSummaryService stockSummaryService;

    // GET /api/stock (legacy)
    @GetMapping
    public List<StockItemDTO> listarStock() {
        return stockService.listarStock();
    }

    // New: GET /api/stock/products -> paginated product summaries (searchable, filterable)
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> listarResumenProductos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String lab,
            @RequestParam(required = false) String cat,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ProductSummaryDTO> paged = stockSummaryService.getProductSummaries(q, lab, cat, PageRequest.of(page, size));
        Map<String, Object> response = new HashMap<>();
        response.put("content", paged.getContent());
        response.put("totalElements", paged.getTotalElements());
        response.put("totalPages", paged.getTotalPages());
        response.put("page", paged.getNumber());
        response.put("size", paged.getSize());
        return ResponseEntity.ok(response);
    }

    // PUT /api/stock/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizarStock(@PathVariable int id, @RequestBody StockItemDTO dto) {
        stockService.actualizarStock(id, dto);
        return ResponseEntity.ok().build();
    }
}