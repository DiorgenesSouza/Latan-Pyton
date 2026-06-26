package com.projetolatan.service;

import com.projetolatan.entity.VooLATAM;
import com.projetolatan.repository.VooLATAMRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class VooExcelService {

    private static final String[] COLUNAS = {
            "ID",
            "Origem",
            "Destino",
            "Data do Voo",
            "Partida",
            "Chegada",
            "Duracao",
            "Preco",
            "Data da Consulta"
    };

    private final VooLATAMRepository vooLATAMRepository;

    public VooExcelService(VooLATAMRepository vooLATAMRepository) {
        this.vooLATAMRepository = vooLATAMRepository;
    }

    public byte[] gerarPlanilhaVoos() {
        List<VooLATAM> voos = vooLATAMRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Voos LATAM");
            CellStyle headerStyle = criarHeaderStyle(workbook);

            criarCabecalho(sheet, headerStyle);
            preencherLinhas(sheet, voos);

            for (int i = 0; i < COLUNAS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar planilha Excel.", e);
        }
    }

    private CellStyle criarHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void criarCabecalho(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(0);

        for (int i = 0; i < COLUNAS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(COLUNAS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void preencherLinhas(Sheet sheet, List<VooLATAM> voos) {
        DateTimeFormatter dataFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter dataHoraFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < voos.size(); i++) {
            VooLATAM voo = voos.get(i);
            Row row = sheet.createRow(i + 1);

            row.createCell(0).setCellValue(voo.getId() != null ? voo.getId() : 0);
            row.createCell(1).setCellValue(voo.getOrigem());
            row.createCell(2).setCellValue(voo.getDestino());
            row.createCell(3).setCellValue(voo.getDataVoo() != null
                    ? voo.getDataVoo().format(dataFormatter)
                    : "");
            row.createCell(4).setCellValue(voo.getHorarioPartida());
            row.createCell(5).setCellValue(voo.getHorarioChegada());
            row.createCell(6).setCellValue(voo.getDuracao());
            row.createCell(7).setCellValue(voo.getPreco() != null ? voo.getPreco().doubleValue() : 0);
            row.createCell(8).setCellValue(voo.getDataConsulta() != null
                    ? voo.getDataConsulta().format(dataHoraFormatter)
                    : "");
        }
    }
}
