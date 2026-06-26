import argparse
import asyncio
import json
import re
import sys
from datetime import datetime
from urllib.parse import urlencode
from uuid import uuid4

from playwright.async_api import TimeoutError as PlaywrightTimeoutError
from playwright.async_api import async_playwright


LATAM_HOME_URL = "https://www.latamairlines.com/br/pt"
LATAM_SEARCH_URL = "https://www.latamairlines.com/br/pt/oferta-voos"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/126.0.0.0 Safari/537.36"
)


def validar_data(data: str) -> str:
    """
    Valida YYYY-MM-DD e devolve no formato usado pela busca da LATAM.
    Exemplo: 2026-06-30T00:00:00.000Z
    """
    try:
        data_obj = datetime.strptime(data, "%Y-%m-%d")
    except ValueError as exc:
        raise ValueError("A data deve estar no formato YYYY-MM-DD.") from exc

    return data_obj.strftime("%Y-%m-%dT00:00:00.000Z")


def montar_url(origem: str, destino: str, data: str) -> str:
    outbound = validar_data(data)

    params = urlencode(
        {
            "origin": origem.upper(),
            "destination": destino.upper(),
            "outbound": outbound,
            "inbound": "null",
            "adt": "1",
            "chd": "0",
            "inf": "0",
            "trip": "OW",
            "cabin": "Economy",
            "redemption": "false",
            "sort": "PRICE,asc",
            "exp_id": str(uuid4()),
        }
    )
    return f"{LATAM_SEARCH_URL}?{params}"


def normalizar_preco(preco_texto: str) -> float | None:
    if not preco_texto:
        return None

    match = re.search(
        r"(?:R\$|BRL)\s*([\d.]+(?:,\d{1,2})?)",
        preco_texto,
        flags=re.IGNORECASE,
    )
    if not match:
        return None

    valor = match.group(1).replace(".", "").replace(",", ".")

    try:
        return float(valor)
    except ValueError:
        return None


def extrair_duracao(texto: str) -> str | None:
    padroes = [
        r"\b\d+\s*h(?:\s*\d+\s*min)?\b",
        r"\b\d+\s*hora(?:s)?(?:\s*e\s*\d+\s*minuto(?:s)?)?\b",
        r"\b\d+\s*min\b",
    ]

    for padrao in padroes:
        match = re.search(padrao, texto, flags=re.IGNORECASE)
        if match:
            return re.sub(r"\s+", " ", match.group(0)).strip()

    return None


def extrair_voo_do_texto(
    texto: str,
    origem: str,
    destino: str,
    data: str,
) -> dict | None:
    texto = re.sub(r"\s+", " ", texto).strip()

    horarios = re.findall(r"\b(?:[01]\d|2[0-3]):[0-5]\d\b", texto)
    precos_texto = re.findall(
        r"(?:R\$|BRL)\s*[\d.]+(?:,\d{1,2})?",
        texto,
        flags=re.IGNORECASE,
    )

    if len(horarios) < 2 or not precos_texto:
        return None

    precos = [
        valor
        for valor in map(normalizar_preco, precos_texto)
        if valor is not None and valor > 0
    ]

    if not precos:
        return None

    return {
        "origem": origem.upper(),
        "destino": destino.upper(),
        "dataVoo": data,
        "horarioPartida": horarios[0],
        "horarioChegada": horarios[1],
        "duracao": extrair_duracao(texto),
        "preco": min(precos),
    }


async def aceitar_cookies_se_aparecer(page) -> None:
    print("Verificando a presença do banner de cookies...")

    seletores = [
        '#cookies-politics button:has-text("Aceitar")',
        '#cookies-politics button:has-text("Aceite todos os cookies")',
        'button:has-text("Aceitar todos")',
        'div[id="cookies-politics"] button:nth-of-type(1)',
    ]

    for seletor in seletores:
        try:
            botao = page.locator(seletor).first
            if await botao.count() and await botao.is_visible(timeout=1500):
                await botao.click(force=True, timeout=5000)
                print(f"Banner de cookies fechado usando: {seletor}")
                await page.wait_for_timeout(1500)
                return
        except Exception:
            continue

    print("Banner de cookies não foi detectado ou já estava fechado.")


async def pagina_tem_erro(page) -> bool:
    url = page.url.lower()

    try:
        texto = (await page.locator("body").inner_text(timeout=10000)).lower()
    except Exception:
        texto = ""

    sinais = [
        "tempo-resultados-busca",
        "a busca está demorando mais que o normal",
        "tivemos um problema com os resultados",
        "página não encontrada",
        "pagina nao encontrada",
        "access denied",
        "acesso negado",
    ]

    conteudo = f"{url}\n{texto}"
    return any(sinal in conteudo for sinal in sinais)


async def preparar_sessao(page) -> None:
    await page.goto(
        LATAM_HOME_URL,
        wait_until="domcontentloaded",
        timeout=60000,
    )

    await aceitar_cookies_se_aparecer(page)

    try:
        await page.wait_for_load_state("networkidle", timeout=15000)
    except PlaywrightTimeoutError:
        pass

    await page.wait_for_timeout(2000)


async def abrir_busca_com_tentativas(
    page,
    origem: str,
    destino: str,
    data: str,
    tentativas: int = 3,
) -> str:
    ultimo_erro = ""

    for tentativa in range(1, tentativas + 1):
        url = montar_url(origem, destino, data)
        print(f"Abrindo busca na LATAM — tentativa {tentativa}/{tentativas}...")

        try:
            response = await page.goto(
                url,
                wait_until="domcontentloaded",
                timeout=90000,
            )

            if response and response.status >= 400:
                ultimo_erro = f"HTTP {response.status}"
            else:
                await page.wait_for_timeout(8000)

                if not await pagina_tem_erro(page):
                    return url

                ultimo_erro = (
                    "A LATAM redirecionou para a página "
                    "'A busca está demorando mais que o normal'."
                )

        except PlaywrightTimeoutError:
            ultimo_erro = "Tempo limite ao abrir a página."
        except Exception as exc:
            ultimo_erro = f"{type(exc).__name__}: {exc}"

        await page.screenshot(
            path=f"latam_tentativa_{tentativa}.png",
            full_page=True,
        )

        if tentativa < tentativas:
            espera = tentativa * 5
            print(f"Falha na tentativa {tentativa}. Nova tentativa em {espera}s...")
            await page.wait_for_timeout(espera * 1000)
            await preparar_sessao(page)

    raise RuntimeError(
        f"A busca falhou após {tentativas} tentativas. Último retorno: {ultimo_erro} "
        "Foram geradas as imagens latam_tentativa_1.png, "
        "latam_tentativa_2.png e latam_tentativa_3.png."
    )


async def esperar_precos(page) -> None:
    if await pagina_tem_erro(page):
        raise RuntimeError(
            "A LATAM redirecionou para a tela de erro antes de carregar os preços."
        )

    seletor_preco = page.get_by_text(
        re.compile(r"(?:R\$|BRL)\s*[\d.]+(?:,\d{1,2})?", re.I)
    ).first

    try:
        await seletor_preco.wait_for(state="visible", timeout=60000)
    except PlaywrightTimeoutError as exc:
        await page.screenshot(path="latam_sem_precos.png", full_page=True)

        if await pagina_tem_erro(page):
            raise RuntimeError(
                "A LATAM apresentou a mensagem "
                "'A busca está demorando mais que o normal'."
            ) from exc

        raise RuntimeError(
            "A página abriu, mas nenhum preço apareceu. "
            "Foi gerada a imagem latam_sem_precos.png."
        ) from exc


async def textos_dos_cards(page) -> list[str]:
    js_code = r"""
    () => {
        const selectors = [
            '[role="listitem"]',
            '[data-testid*="flight" i]',
            '[data-testid*="itinerary" i]',
            '[data-testid*="card" i]',
            '[class*="flight" i]',
            '[class*="itinerary" i]'
        ];

        const nodes = [...document.querySelectorAll(selectors.join(','))];

        const looksLikeFlight = (text) => {
            const hasPrice = /(?:R\$|BRL)\s*[\d.]+(?:,\d{1,2})?/i.test(text);
            const times = text.match(/\b(?:[01]\d|2[0-3]):[0-5]\d\b/g) || [];
            return hasPrice && times.length >= 2;
        };

        const unique = new Set();

        for (const node of nodes) {
            const text = (node.innerText || '').replace(/\s+/g, ' ').trim();

            if (text.length < 20 || text.length > 1800 || !looksLikeFlight(text)) {
                continue;
            }

            unique.add(text);
        }

        return [...unique];
    }
    """
    return await page.evaluate(js_code)


async def buscar_voos_latam(
    origem: str,
    destino: str,
    data: str,
    manter_aberto: bool = False,
) -> list[dict]:
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)

        context = await browser.new_context(
            user_agent=USER_AGENT,
            locale="pt-BR",
            viewport={"width": 1366, "height": 768},
            timezone_id="America/Sao_Paulo",
        )

        page = await context.new_page()

        try:
            await preparar_sessao(page)

            await abrir_busca_com_tentativas(
                page=page,
                origem=origem,
                destino=destino,
                data=data,
                tentativas=3,
            )

            await aceitar_cookies_se_aparecer(page)
            await esperar_precos(page)

            textos = await textos_dos_cards(page)
            voos: list[dict] = []
            vistos = set()

            for texto in textos:
                voo = extrair_voo_do_texto(texto, origem, destino, data)

                if not voo:
                    continue

                chave = (
                    voo["horarioPartida"],
                    voo["horarioChegada"],
                    voo["duracao"],
                    voo["preco"],
                )

                if chave in vistos:
                    continue

                vistos.add(chave)
                voos.append(voo)

            voos.sort(key=lambda item: (item["preco"], item["horarioPartida"]))

            if not voos:
                await page.screenshot(
                    path="latam_cards_nao_encontrados.png",
                    full_page=True,
                )
                raise RuntimeError(
                    "Os preços apareceram, mas nenhum card de voo válido foi lido. "
                    "Foi gerada a imagem latam_cards_nao_encontrados.png."
                )

            if manter_aberto:
                await asyncio.to_thread(
                    input,
                    "\nNavegador mantido aberto. Pressione Enter para encerrar...",
                )

            return voos

        except Exception:
            try:
                await page.screenshot(path="latam_erro_final.png", full_page=True)
            except Exception:
                pass
            raise
        finally:
            await context.close()
            await browser.close()


async def main() -> None:
    parser = argparse.ArgumentParser(
        description="Busca voos LATAM e retorna JSON."
    )
    parser.add_argument("origem", help="Código IATA de origem. Ex.: GRU")
    parser.add_argument("destino", help="Código IATA de destino. Ex.: SDU")
    parser.add_argument("data", help="Data do voo no formato YYYY-MM-DD")
    parser.add_argument(
        "--manter-aberto",
        action="store_true",
        help="Mantém o navegador aberto até pressionar Enter.",
    )
    args = parser.parse_args()

    try:
        voos = await buscar_voos_latam(
            args.origem,
            args.destino,
            args.data,
            manter_aberto=args.manter_aberto,
        )

        print(json.dumps(voos, ensure_ascii=False, indent=2))

    except Exception as exc:
        print(
            json.dumps(
                {"erro": f"{type(exc).__name__}: {exc}"},
                ensure_ascii=False,
                indent=2,
            ),
            file=sys.stderr,
        )
        sys.exit(2)


if __name__ == "__main__":
    asyncio.run(main())
