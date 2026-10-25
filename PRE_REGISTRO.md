# PRE_REGISTRO.md — Hipóteses pré-registradas

H1 — ordenação. O F1@5 médio segue a ordem Adaptativo > Semântico > Fixo, como em Gomez-Cabello et al. (2025).

H2 — teto estrutural. O estrato abrangente tem a menor revocação em todas as estratégias, como em Sarthi et al. (2024).

H3 — vocabulário. A queda das consultas terminológicas em relação às factuais é menor do que a queda das abrangentes, porque os embeddings captam paráfrase (Seção 2.1 do TCC).

H4 — custo. A latência de busca não difere materialmente entre as estratégias nesta escala. O custo de indexação difere, e o adaptativo é o mais caro, porque exige os embeddings de todas as unidades, além dos embeddings dos chunks, mais a rodada de calibração.
