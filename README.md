# LATAM Flight Scraper

Projeto em Python que utiliza **Playwright** para abrir o site da LATAM, pesquisar voos e retornar os resultados em formato JSON.

O script recebe:

- aeroporto de origem;
- aeroporto de destino;
- data do voo.

Como resultado, tenta extrair:

- horário de partida;
- horário de chegada;
- duração do voo;
- preço.

## Tecnologias utilizadas

- Python 3.10 ou superior
- Playwright
- Chromium
- AsyncIO

## Estrutura principal

```text
Projeto Latan/
├── latam_scraper_corrigido_v3.py
├── README.md
└── imagens de evidência geradas em caso de erro
```

## Pré-requisitos

Confirme se o Python está instalado:

```powershell
python --version
```

Também é possível usar:

```powershell
py --version
```

## Instalação

Abra o PowerShell dentro da pasta do projeto:

```powershell
cd "C:\Projeto Latan"
```

Instale o Playwright:

```powershell
python -m pip install playwright
```

Instale o navegador Chromium utilizado pelo Playwright:

```powershell
python -m playwright install chromium
```

## Como executar

Formato do comando:

```powershell
python .\latam_scraper_corrigido_v3.py ORIGEM DESTINO DATA
```

Exemplo:

```powershell
python .\latam_scraper_corrigido_v3.py GRU SDU 2026-06-30
```

A data deve ser informada no formato:

```text
YYYY-MM-DD
```

Exemplo:

```text
2026-06-30
```

## Manter o navegador aberto

Para manter o navegador aberto após o término da execução ou depois de um erro, use:

```powershell
python .\latam_scraper_corrigido_v3.py GRU SDU 2026-06-30 --manter-aberto
```

Nesse modo, o navegador somente será encerrado depois que você pressionar `Enter` no terminal.

## Exemplo de retorno

```json
[
  {
    "origem": "GRU",
    "destino": "SDU",
    "dataVoo": "2026-06-30",
    "horarioPartida": "12:15",
    "horarioChegada": "13:10",
    "duracao": "0 h 55 min",
    "preco": 417.17
  }
]
```

## Tratamento de erros

O script identifica situações como:

- página de resultados não carregada;
- mensagem “A busca está demorando mais que o normal”;
- preços não exibidos;
- cards de voo não encontrados;
- erro HTTP;
- data em formato inválido.

Quando ocorre uma falha, o retorno é semelhante a:

```json
{
  "erro": "RuntimeError: A LATAM apresentou a mensagem 'A busca está demorando mais que o normal'."
}
```

## Evidências geradas

Quando a busca falha, o script pode gerar imagens na pasta do projeto:

```text
latam_tentativa_1.png
latam_tentativa_2.png
latam_tentativa_3.png
latam_sem_precos.png
latam_cards_nao_encontrados.png
latam_erro_final.png
```

Essas imagens mostram como a página estava no momento do erro.

## Funcionamento da busca

O script realiza os seguintes passos:

1. abre a página inicial da LATAM;
2. tenta aceitar o banner de cookies;
3. monta a URL da pesquisa;
4. abre a busca do voo;
5. realiza até três tentativas em caso de falha;
6. espera os preços aparecerem;
7. identifica os cards de voo;
8. extrai os dados;
9. remove resultados duplicados;
10. ordena os voos pelo menor preço;
11. imprime o resultado em JSON.

## Problemas conhecidos

O site da LATAM pode:

- demorar para retornar os resultados;
- redirecionar para uma página de erro;
- alterar a estrutura HTML;
- apresentar CAPTCHA;
- bloquear ou limitar acessos automatizados;
- retornar resultados diferentes conforme sessão, localização ou disponibilidade.

Quando isso acontecer, o problema pode estar no retorno do próprio site, e não necessariamente no código.

## Solução de problemas

### Arquivo não encontrado

Erro:

```text
can't open file
No such file or directory
```

Confirme se o arquivo está na pasta atual:

```powershell
dir
```

Depois execute usando o caminho relativo:

```powershell
python .\latam_scraper_corrigido_v3.py GRU SDU 2026-06-30
```

### Playwright não encontrado

Execute:

```powershell
python -m pip install playwright
```

### Chromium não instalado

Execute:

```powershell
python -m playwright install chromium
```

### Navegador fecha depois do erro

Execute com:

```powershell
python .\latam_scraper_corrigido_v3.py GRU SDU 2026-06-30 --manter-aberto
```

### A busca está demorando mais que o normal

Essa mensagem é retornada pela própria LATAM. Tente:

- executar novamente alguns minutos depois;
- testar outra data;
- testar outra rota;
- abrir a mesma busca manualmente no navegador;
- verificar se apareceu CAPTCHA;
- conferir as imagens de evidência geradas.

## Observação

Este projeto é destinado a estudos e testes de automação. O uso deve respeitar os termos, regras e limites do site consultado.
