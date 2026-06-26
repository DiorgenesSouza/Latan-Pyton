package com.projetolatan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projetolatan.entity.VooLATAM;
import com.projetolatan.repository.VooLATAMRepository;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScraperService {

    private final VooLATAMRepository vooLATAMRepository;
    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final String scraperPath;
    private final long timeoutSeconds;

    public ScraperService(
            VooLATAMRepository vooLATAMRepository,
            ObjectMapper objectMapper,
            @Value("${scraper.python-command:python}") String pythonCommand,
            @Value("${scraper.script-path:latam_scraper.py}") String scraperPath,
            @Value("${scraper.timeout-seconds:180}") long timeoutSeconds
    ) {
        this.vooLATAMRepository = vooLATAMRepository;
        this.objectMapper = objectMapper;
        this.pythonCommand = pythonCommand;
        this.scraperPath = scraperPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<VooLATAM> buscarESalvarVoos(String origem, String destino, LocalDate data) {
        validarParametros(origem, destino, data);

        try {
            File scriptFile = new File(scraperPath);
            File workingDirectory = scriptFile.getParentFile();

            String scriptCommand = workingDirectory != null ? scriptFile.getName() : scraperPath;
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonCommand,
                    scriptCommand,
                    origem.toUpperCase(),
                    destino.toUpperCase(),
                    data.toString()
            );

            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory);
            }

            Process process = processBuilder.start();
            CompletableFuture<String> stdout = lerStream(process.getInputStream());
            CompletableFuture<String> stderr = lerStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Tempo limite excedido ao executar o scraper LATAM.");
            }

            String output = stdout.join().trim();
            String errors = stderr.join().trim();

            if (process.exitValue() != 0) {
                throw new IllegalStateException("Erro ao executar o scraper LATAM: " + extrairMensagemErro(errors));
            }

            List<VooLATAM> voos = objectMapper.readValue(output, new TypeReference<>() {
            });

            LocalDateTime dataConsulta = LocalDateTime.now();
            voos.forEach(voo -> {
                voo.setId(null);
                voo.setOrigem(origem.toUpperCase());
                voo.setDestino(destino.toUpperCase());
                voo.setDataVoo(data);
                voo.setDataConsulta(dataConsulta);
            });

            return vooLATAMRepository.saveAll(voos);
        } catch (IOException e) {
            throw new IllegalStateException("Falha de IO ao executar o scraper LATAM.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execucao do scraper LATAM interrompida.", e);
        }
    }

    private CompletableFuture<String> lerStream(java.io.InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder texto = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    texto.append(linha).append(System.lineSeparator());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler saida do processo Python.", e);
            }

            return texto.toString();
        });
    }

    private void validarParametros(String origem, String destino, LocalDate data) {
        if (!StringUtils.hasText(origem) || !origem.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Origem deve conter um codigo IATA com 3 letras.");
        }

        if (!StringUtils.hasText(destino) || !destino.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Destino deve conter um codigo IATA com 3 letras.");
        }

        if (data == null) {
            throw new IllegalArgumentException("Data do voo e obrigatoria.");
        }
    }

    private String extrairMensagemErro(String errors) {
        if (!StringUtils.hasText(errors)) {
            return "O processo Python terminou com erro, mas nao retornou detalhes.";
        }

        try {
            JsonNode node = objectMapper.readTree(errors);
            if (node.hasNonNull("erro")) {
                return node.get("erro").asText();
            }
        } catch (IOException ignored) {
            // Quando nao for JSON, retorna a saida original abaixo.
        }

        return errors;
    }
}
