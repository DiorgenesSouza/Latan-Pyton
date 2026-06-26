package com.projetolatan.controller;

import com.projetolatan.dto.BuscarVoosRequest;
import com.projetolatan.entity.VooLATAM;
import com.projetolatan.service.ScraperService;
import com.projetolatan.service.VooExcelService;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/voos")
public class VooController {

    private final ScraperService scraperService;
    private final VooExcelService vooExcelService;

    public VooController(ScraperService scraperService, VooExcelService vooExcelService) {
        this.scraperService = scraperService;
        this.vooExcelService = vooExcelService;
    }

    @PostMapping("/buscar")
    public ResponseEntity<?> buscarVoos(
            @RequestBody(required = false) BuscarVoosRequest body,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String destino,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        String origemBusca = origem != null ? origem : body != null ? body.getOrigem() : null;
        String destinoBusca = destino != null ? destino : body != null ? body.getDestino() : null;
        LocalDate dataBusca = data != null ? data : body != null ? body.getData() : null;

        try {
            List<VooLATAM> voos = scraperService.buscarESalvarVoos(origemBusca, destinoBusca, dataBusca);
            return ResponseEntity.ok(voos);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("erro", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("erro", exception.getMessage()));
        }
    }

    @GetMapping("/exportar-excel")
    public ResponseEntity<Resource> exportarExcel() {
        byte[] arquivo = vooExcelService.gerarPlanilhaVoos();
        ByteArrayResource resource = new ByteArrayResource(arquivo);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("voos-latam.xlsx")
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(arquivo.length)
                .body(resource);
    }

}
