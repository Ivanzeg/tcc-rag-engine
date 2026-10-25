import json
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

RAIZ = Path(__file__).resolve().parent.parent
SAIDA_TABELAS = RAIZ / "analysis" / "tables"
SAIDA_FIGURAS = RAIZ / "analysis" / "figures"

SEMENTE = 2026
REAMOSTRAGENS = 10000
DPI = 300

ESTRATEGIAS = ["fixed", "semantic", "adaptive"]
ROTULOS = {
    "fixed": "Fixo (linha de base)",
    "semantic": "Semântico",
    "adaptive": "Adaptativo",
}
ESTRATOS = ["FACTUAL", "TERMINOLOGICAL", "COMPREHENSIVE"]
ROTULOS_ESTRATO = {
    "FACTUAL": "Factual",
    "TERMINOLOGICAL": "Terminológico",
    "COMPREHENSIVE": "Abrangente",
}
METRICAS = [("p_at_5", "P@5"), ("r_at_5", "R@5"), ("f1_at_5", "F1@5"), ("rr_at_5", "MRR@5")]
CORES = {"fixed": "#6b7280", "semantic": "#2563eb", "adaptive": "#d97706"}

NOTAS_INDEXACAO = {
    "fixed": "Todos os embeddings vieram do cache em disco (nenhuma chamada à API); index_seconds não inclui tempo de rede.",
    "semantic": "26 de 27 embeddings vieram da API e 1 do cache; index_seconds inclui tempo de rede.",
    "adaptive": "63 de 75 embeddings de chunks vieram da API e 12 do cache; index_seconds inclui tempo de rede.",
}


def avisar(mensagem):
    print(f"[aviso] {mensagem}", file=sys.stderr)


def informar(mensagem):
    print(f"[info] {mensagem}")


def carregar_dados():
    metricas = pd.concat(
        [pd.read_csv(RAIZ / "results" / f"metrics_{s}.csv") for s in ESTRATEGIAS],
        ignore_index=True,
    )
    metricas = metricas.sort_values(["strategy", "query_id"]).reset_index(drop=True)
    ids_por_estrategia = [
        tuple(metricas.loc[metricas["strategy"] == s, "query_id"]) for s in ESTRATEGIAS
    ]
    if len(set(ids_por_estrategia)) != 1:
        raise SystemExit("As três estratégias não avaliam o mesmo conjunto de consultas.")

    custo = pd.read_csv(RAIZ / "results" / "indexing_cost.csv")
    repetidas = custo["strategy"].value_counts()
    for estrategia, quantidade in repetidas.items():
        if quantidade > 1:
            avisar(
                f"indexing_cost.csv tem {quantidade} linhas para '{estrategia}'; "
                "usando a mais recente pelo timestamp."
            )
    custo = custo.sort_values("timestamp").groupby("strategy", as_index=False).tail(1)
    custo = custo.set_index("strategy")

    chunks = {}
    for s in ESTRATEGIAS:
        caminho = RAIZ / "data" / "chunks" / f"{s}.jsonl"
        with open(caminho, encoding="utf-8") as arquivo:
            chunks[s] = [json.loads(linha) for linha in arquivo if linha.strip()]

    with open(RAIZ / "data" / "corpus" / "stats.json", encoding="utf-8") as arquivo:
        stats = json.load(arquivo)
    with open(RAIZ / "data" / "calibration" / "adaptive_choice.json", encoding="utf-8") as arquivo:
        escolha = json.load(arquivo)
    similaridades = pd.read_csv(RAIZ / "data" / "calibration" / "adaptive_similarities.csv")

    return metricas, custo, chunks, stats, escolha, similaridades


def matriz_metrica(metricas, coluna):
    return np.vstack(
        [metricas.loc[metricas["strategy"] == s, coluna].to_numpy() for s in ESTRATEGIAS]
    )


def indices_bootstrap(n_consultas, rng):
    return rng.integers(0, n_consultas, size=(REAMOSTRAGENS, n_consultas))


def medias_reamostradas(valores, indices):
    return valores[indices].mean(axis=1)


def ic_percentil(medias):
    inferior, superior = np.percentile(medias, [2.5, 97.5])
    return float(inferior), float(superior)


def t_corpus(metricas, stats):
    consultas = metricas[metricas["strategy"] == "fixed"]
    linhas = [
        ("unidades", stats["units"]),
        ("dispositivos (PROVISION)", stats["provisions"]),
        ("titulos (HEADING)", stats["headings"]),
        ("artigos", stats["articles"]),
        ("capitulos", stats["chapters"]),
        ("palavras", stats["words"]),
        ("caracteres", stats["characters"]),
        ("pagina_inicial_recorte", stats["pdfStartPage"]),
        ("pagina_final_recorte", stats["pdfEndPage"]),
        ("sha256_pdf", stats["pdfSha256"]),
        ("sha256_lgpd_txt", stats["corpusSha256"]),
        ("consultas_total", len(consultas)),
    ]
    for estrato in ESTRATOS:
        linhas.append(
            (f"consultas_{ROTULOS_ESTRATO[estrato].lower()}", int((consultas["stratum"] == estrato).sum()))
        )
    return pd.DataFrame(linhas, columns=["indicador", "valor"])


def t_chunks(chunks, custo):
    linhas = []
    for s in ESTRATEGIAS:
        caracteres = pd.Series([len(c["text"]) for c in chunks[s]])
        palavras = pd.Series([len(c["text"].split()) for c in chunks[s]])
        linhas.append(
            {
                "estrategia": ROTULOS[s],
                "n_chunks": len(chunks[s]),
                "caracteres_media": caracteres.mean(),
                "caracteres_desvio_padrao": caracteres.std(),
                "caracteres_minimo": caracteres.min(),
                "caracteres_maximo": caracteres.max(),
                "palavras_media": palavras.mean(),
                "index_seconds": custo.loc[s, "index_seconds"],
                "nota_index_seconds": NOTAS_INDEXACAO[s],
            }
        )
    return pd.DataFrame(linhas)


def t_global(metricas, indices):
    linhas = []
    for i, s in enumerate(ESTRATEGIAS):
        linha = {"estrategia": ROTULOS[s]}
        for coluna, rotulo in METRICAS:
            valores = matriz_metrica(metricas, coluna)[i]
            inferior, superior = ic_percentil(medias_reamostradas(valores, indices))
            nome = rotulo.lower().replace("@5", "")
            linha[f"{nome}_media"] = valores.mean()
            linha[f"{nome}_ic95_inferior"] = inferior
            linha[f"{nome}_ic95_superior"] = superior
        latencia = matriz_metrica(metricas, "latency_ms")[i]
        linha["latencia_mediana_ms"] = np.median(latencia)
        linha["latencia_p95_ms"] = np.percentile(latencia, 95)
        linhas.append(linha)
    return pd.DataFrame(linhas)


def t_estrato(metricas, rng):
    referencia = metricas[metricas["strategy"] == "fixed"].reset_index(drop=True)
    linhas = []
    for estrato in ESTRATOS:
        posicoes = np.flatnonzero((referencia["stratum"] == estrato).to_numpy())
        sorteio = rng.integers(0, len(posicoes), size=(REAMOSTRAGENS, len(posicoes)))
        indices = posicoes[sorteio]
        for i, s in enumerate(ESTRATEGIAS):
            for coluna, rotulo in METRICAS[:3]:
                valores = matriz_metrica(metricas, coluna)[i]
                inferior, superior = ic_percentil(medias_reamostradas(valores, indices))
                linhas.append(
                    {
                        "estrategia": ROTULOS[s],
                        "estrato": ROTULOS_ESTRATO[estrato],
                        "metrica": rotulo,
                        "n": len(posicoes),
                        "media": valores[posicoes].mean(),
                        "ic95_inferior": inferior,
                        "ic95_superior": superior,
                    }
                )
    return pd.DataFrame(linhas)


def t_pareado(metricas, indices):
    f1 = matriz_metrica(metricas, "f1_at_5")
    posicao = {s: i for i, s in enumerate(ESTRATEGIAS)}
    comparacoes = [("adaptive", "fixed"), ("semantic", "fixed"), ("adaptive", "semantic")]
    linhas = []
    for a, b in comparacoes:
        diferenca = f1[posicao[a]] - f1[posicao[b]]
        inferior, superior = ic_percentil(medias_reamostradas(diferenca, indices))
        linhas.append(
            {
                "comparacao": f"{ROTULOS[a]} − {ROTULOS[b]}",
                "n_consultas": len(diferenca),
                "diferenca_media_f1": diferenca.mean(),
                "ic95_inferior": inferior,
                "ic95_superior": superior,
            }
        )
    return pd.DataFrame(linhas)


def t_hipoteses_dados(metricas, custo):
    linhas = []
    for s in ESTRATEGIAS:
        sub = metricas[metricas["strategy"] == s]
        por_estrato = sub.groupby("stratum")[["r_at_5", "f1_at_5"]].mean()
        menor = por_estrato["r_at_5"].idxmin()
        factual = por_estrato.loc["FACTUAL"]
        linha = {
            "estrategia": ROTULOS[s],
            "f1_global": sub["f1_at_5"].mean(),
            "estrato_menor_revocacao": ROTULOS_ESTRATO[menor],
            "revocacao_do_estrato_menor": por_estrato.loc[menor, "r_at_5"],
        }
        for estrato in ["TERMINOLOGICAL", "COMPREHENSIVE"]:
            nome = ROTULOS_ESTRATO[estrato].lower()
            linha[f"queda_f1_{nome}_vs_factual"] = factual["f1_at_5"] - por_estrato.loc[estrato, "f1_at_5"]
            linha[f"queda_revocacao_{nome}_vs_factual"] = factual["r_at_5"] - por_estrato.loc[estrato, "r_at_5"]
        linha["latencia_mediana_ms"] = sub["latency_ms"].median()
        linha["latencia_p95_ms"] = np.percentile(sub["latency_ms"], 95)
        linha["embedding_inputs"] = int(custo.loc[s, "embedding_inputs"])
        linhas.append(linha)
    return pd.DataFrame(linhas)


def formatar(valor, casas=3):
    return f"{valor:.{casas}f}".replace(".", ",")


def f_barras(tabela_global, caminho):
    grupos = [("p", "P@5"), ("r", "R@5"), ("f1", "F1@5")]
    largura = 0.26
    posicoes = np.arange(len(grupos))
    figura, eixo = plt.subplots(figsize=(7.5, 4.5))
    for deslocamento, s in enumerate(ESTRATEGIAS):
        linha = tabela_global[tabela_global["estrategia"] == ROTULOS[s]].iloc[0]
        medias = np.array([linha[f"{g}_media"] for g, _ in grupos])
        inferior = medias - np.array([linha[f"{g}_ic95_inferior"] for g, _ in grupos])
        superior = np.array([linha[f"{g}_ic95_superior"] for g, _ in grupos]) - medias
        eixo.bar(
            posicoes + (deslocamento - 1) * largura,
            medias,
            largura,
            yerr=[inferior, superior],
            capsize=3,
            label=ROTULOS[s],
            color=CORES[s],
        )
    eixo.set_xticks(posicoes)
    eixo.set_xticklabels([rotulo for _, rotulo in grupos])
    eixo.set_ylabel("Média sobre as 24 consultas (IC 95%)")
    eixo.set_ylim(0, 1)
    eixo.yaxis.set_major_formatter(lambda valor, _: formatar(valor, 1))
    eixo.legend(frameon=False)
    eixo.spines[["top", "right"]].set_visible(False)
    figura.tight_layout()
    figura.savefig(caminho, dpi=DPI)
    plt.close(figura)


def f_heatmap(tabela_estrato, caminho):
    f1 = tabela_estrato[tabela_estrato["metrica"] == "F1@5"]
    matriz = np.array(
        [
            [
                f1[(f1["estrategia"] == ROTULOS[s]) & (f1["estrato"] == ROTULOS_ESTRATO[e])]["media"].iloc[0]
                for e in ESTRATOS
            ]
            for s in ESTRATEGIAS
        ]
    )
    figura, eixo = plt.subplots(figsize=(6.5, 3.6))
    imagem = eixo.imshow(matriz, cmap="viridis", vmin=0, vmax=max(1.0, matriz.max()), aspect="auto")
    eixo.set_xticks(range(len(ESTRATOS)))
    eixo.set_xticklabels([ROTULOS_ESTRATO[e] for e in ESTRATOS])
    eixo.set_yticks(range(len(ESTRATEGIAS)))
    eixo.set_yticklabels([ROTULOS[s] for s in ESTRATEGIAS])
    for i in range(matriz.shape[0]):
        for j in range(matriz.shape[1]):
            eixo.text(
                j,
                i,
                formatar(matriz[i, j]),
                ha="center",
                va="center",
                color="white" if matriz[i, j] < max(1.0, matriz.max()) * 0.6 else "black",
            )
    barra = figura.colorbar(imagem, ax=eixo)
    barra.set_label("F1@5 médio")
    figura.tight_layout()
    figura.savefig(caminho, dpi=DPI)
    plt.close(figura)


def f_calibracao(similaridades, escolha, caminho):
    tau = escolha["tau"]
    figura, eixo = plt.subplots(figsize=(7, 4.2))
    eixo.hist(similaridades["similarity"], bins=30, color="#2563eb", edgecolor="white")
    eixo.axvline(tau, color="#dc2626", linestyle="--", linewidth=1.8, label=f"τ* = {formatar(tau, 4)}")
    eixo.set_xlabel("Similaridade de cosseno entre unidades consecutivas")
    eixo.set_ylabel("Número de pares de unidades")
    eixo.xaxis.set_major_formatter(lambda valor, _: formatar(valor, 1))
    eixo.legend(frameon=False)
    eixo.spines[["top", "right"]].set_visible(False)
    figura.tight_layout()
    figura.savefig(caminho, dpi=DPI)
    plt.close(figura)


def gravar(tabela, nome):
    caminho = SAIDA_TABELAS / f"{nome}.csv"
    tabela.to_csv(caminho, index=False, encoding="utf-8", float_format="%.6f")
    informar(f"tabela gravada: {caminho.relative_to(RAIZ)}")


def imprimir(tabela, nome):
    print(f"\n=== {nome} ===")
    with pd.option_context("display.max_columns", None, "display.width", 250, "display.max_colwidth", 120):
        print(tabela.to_string(index=False))


def main():
    SAIDA_TABELAS.mkdir(parents=True, exist_ok=True)
    SAIDA_FIGURAS.mkdir(parents=True, exist_ok=True)

    metricas, custo, chunks, stats, escolha, similaridades = carregar_dados()
    n_consultas = metricas[metricas["strategy"] == "fixed"].shape[0]
    informar(f"{n_consultas} consultas por estratégia; {REAMOSTRAGENS} reamostragens; semente {SEMENTE}")

    rng = np.random.default_rng(SEMENTE)
    indices = indices_bootstrap(n_consultas, rng)

    tabelas = {
        "t_corpus": t_corpus(metricas, stats),
        "t_chunks": t_chunks(chunks, custo),
        "t_global": t_global(metricas, indices),
        "t_estrato": t_estrato(metricas, rng),
        "t_pareado": t_pareado(metricas, indices),
        "t_hipoteses_dados": t_hipoteses_dados(metricas, custo),
    }
    for nome, tabela in tabelas.items():
        gravar(tabela, nome)

    f_barras(tabelas["t_global"], SAIDA_FIGURAS / "f_barras.png")
    f_heatmap(tabelas["t_estrato"], SAIDA_FIGURAS / "f_heatmap.png")
    f_calibracao(similaridades, escolha, SAIDA_FIGURAS / "f_calibracao.png")
    for nome in ["f_barras", "f_heatmap", "f_calibracao"]:
        informar(f"figura gravada: analysis/figures/{nome}.png")

    for nome, tabela in tabelas.items():
        imprimir(tabela, nome)


if __name__ == "__main__":
    main()
