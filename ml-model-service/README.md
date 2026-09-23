# FraudShield ml-model-service

Python 3.11, FastAPI, scikit-learn; porta 8085. Implementa o contrato `POST /predict` consumido pelo `fraud-detection-service`.

## Estrutura

- `app/` — API FastAPI (`main.py`, `prediction.py`, `schemas.py`).
- `ml/` — features compartilhadas entre treino e inferência (`features.py`), treino (`training/train.py`) e o artefato versionado (`models/fraud-v1.0.0.*`).
- `tests/` — testes da API (pytest).
- `research/paysim/` — baseline anterior treinado no dataset PaySim (notebook de EDA, scripts, métricas e modelo). Fica como referência; suas features não correspondem às que o `fraud-detection-service` envia, por isso não é servido. O CSV do PaySim não é versionado: coloque-o em `research/paysim/data/paysim.csv`.

## Executar e testar

Dentro de `ml-model-service/`:

```sh
python3.11 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
.venv/bin/python -m ml.training.train        # opcional: o artefato versionado já permite iniciar
.venv/bin/python -m pytest
.venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8085
```

O scikit-learn fixado (1.6.1) não tem wheels para Python 3.13+; use 3.11, ou rode os testes num container:

```sh
docker run --rm -v "$PWD":/src -w /src python:3.11-slim \
  sh -c "cp -r /src /w && cd /w && pip install -q -r requirements-dev.txt && python -m pytest"
```

```sh
docker build -t fraudshield-ml-model-service .
docker run --rm -p 127.0.0.1:8085:8085 fraudshield-ml-model-service
```

No Compose ele sobe com o perfil `services` (`fraudshield-ml-model-service`), e o `fraud-detection-service` aguarda o health check antes de iniciar. A imagem tem estágios de build/runtime, usuário não-root e health check; o `.dockerignore` envia só código, dependências e modelo. Um processo Uvicorn por container; escale por containers para manter as métricas corretas.

## API

`POST /predict`, `Content-Type: application/json`:

```json
{
  "amount": 1500.00,
  "hourOfDay": 3,
  "dayOfWeek": 1,
  "transactionType": "PIX",
  "isNewDevice": true,
  "isForeignIp": false,
  "transactionsLastHour": 5,
  "transactionsLast24Hours": 12,
  "avgAmountLast30Days": 250.00
}
```

Resposta contém exatamente `fraudScore` (número entre 0 e 1), `modelVersion` (`v1.0.0`) e `inferenceTimeMs` (inteiro). O score é calculado pelo modelo; não é fixado em 0.87. `inferenceTimeMs` usa relógio monotônico de alta resolução, inclui extração de features e `predict_proba`, e trunca para milissegundos inteiros (0 é válido). Exclui validação HTTP, espera pelo worker, serialização e rede.

Todos os campos são obrigatórios. Tipos de transação: `PIX`, `CREDIT`, `DEBIT`; dia ISO 1–7, hora 0–23; contagens inteiras não negativas até o limite de `long` Java; flags booleanas. Dinheiro finito com até 19 dígitos e 4 casas decimais, como `DECIMAL(19,4)` do projeto; `amount > 0`, média >= 0. Payload inválido, JSON malformado ou campos extras retornam 422. OpenAPI em `/docs`.

`GET /health`: 200 `{"status":"UP"}` após carregar o modelo; 503 se indisponível. Ausência/corrupção/incompatibilidade do arquivo impede startup, permitindo ao circuit breaker Java aplicar seu fallback existente. Modelo é carregado uma vez pelo lifespan; não há treino durante requests.

`GET /metrics`: formato Prometheus com `http_server_requests_total` e histograma `http_server_requests_seconds` (`_bucket`, `_count`, `_sum`), labels `method`, `uri` e `status`. Inclui validação 422 e falhas 500; caminhos desconhecidos viram `unmatched` para limitar cardinalidade. `/health` e `/metrics` não geram amostras, seguindo a exclusão de ruído dos serviços Java. O job Prometheus fornece a identificação do serviço. Esta implementação expõe métricas; não inclui exportação de traces OTLP.

## Dinheiro e features

O parser JSON usa `Decimal` diretamente, preservando inclusive números grandes que perderiam casas decimais se passassem primeiro por `float`. Treino e inferência compartilham `ml/features.py`.

O modelo recebe faixas monetárias delimitadas por 10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 50000 e 100000 (limite incluído na faixa superior). A razão valor/média é calculada com Decimal, multiplicada por 100, truncada e limitada a 100000. Média zero produz razão zero e uma flag de ausência de histórico. Contagens são saturadas em 100000. Todos os inteiros resultantes são exatamente representáveis pelo float32 interno do RandomForest. A granularidade é uma escolha explícita das features: valores dentro da mesma faixa podem produzir a mesma decisão, sem arredondamento monetário acidental. O score probabilístico final é float; nenhum saldo é calculado aqui.

## Treino e artefato

`ml/training/train.py` gera 20000 amostras com seed 42, ruído probabilístico e fatores sintéticos de risco (valor, desvio da média, horário, velocidade, dispositivo e IP). Separa 80/20 de treino/teste com estratificação antes do ajuste. RandomForest com 100 árvores, profundidade máxima 10, mínimo 15 amostras por folha, um thread. Flags `--seed`, `--samples`, `--output` permitem retreinar.

`ml/models/fraud-v1.0.0.pkl` contém classificador, versão do modelo, esquema de features e versão do scikit-learn. O `.json` ao lado registra parâmetros e métricas de holdout (ROC AUC e average precision). São resultados em dados sintéticos, não estimativas de desempenho em fraude real; regras de aprovação/flag/negação continuam no serviço Java.

Carregue somente `.pkl` confiável. Treino e runtime usam as mesmas versões fixadas: persistência entre versões do scikit-learn não é suportada ([documentação oficial](https://scikit-learn.org/stable/model_persistence.html)). O startup utiliza [lifespan do FastAPI](https://fastapi.tiangolo.com/advanced/events/).

## Variáveis de ambiente

- `MODEL_PATH`: caminho do `.pkl` confiável. Opcional localmente; padrão resolve `ml-model-service/ml/models/fraud-v1.0.0.pkl` a partir do código, independente do diretório atual. No Docker: `/app/ml/models/fraud-v1.0.0.pkl`.
- `PYTHONDONTWRITEBYTECODE=1`: definida na imagem para não gerar `.pyc`.
- `PYTHONUNBUFFERED=1`: definida na imagem para saída imediata dos logs.

Nenhuma outra variável é necessária. `ML_MODEL_SERVICE_URL` pertence ao consumidor Java; no Compose aponta para `http://ml-model-service:8085`.
