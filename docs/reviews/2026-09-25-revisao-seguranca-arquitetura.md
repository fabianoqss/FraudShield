# Revisão de segurança e arquitetura — 25/09/2026

Revisão do estado local do FraudShield, incluindo alterações ainda não commitadas. Nenhum código de aplicação foi alterado. Foram examinados os fluxos de autenticação, contas/PIX, transações, análise de fraude, ML, ledger, notificações, gateway, configurações, containers, CI e testes relacionados. A análise é de código e de contratos, com verificações locais pontuais; não é um pentest completo nem uma certificação de ausência de vulnerabilidades.

A comparação foi feita com `SECURITY.md`, `README.md`, `docs/API.md`, especificação/plano de PIX e README do ML. Não há acesso ao histórico privado do Claude: “novo” significa que o problema específico não foi encontrado nesses registros, não que outro agente jamais o tenha percebido.

## Achados novos

### 1. Alta — a proteção contra liquidação repetida não é atômica

**Local:** `account-service/src/main/java/com/fraudetection/account_service/services/BalanceLockService.java:45`, `repositories/BalanceLockRepository.java:11` e `repositories/AccountRepository.java:40`.

`applyApproval` lê a reserva, verifica `settled`, salva `true` e movimenta dinheiro. A leitura não bloqueia a linha, não há `@Version` e não existe atualização condicional que eleja um único consumidor. Duas transações de banco podem ler `settled=false` antes de qualquer uma confirmar. Ambas podem executar o débito e crédito; o débito com reserva não verifica saldo ou reserva restante. A unicidade de `transactionId` protege a criação da reserva, não a atualização de uma reserva existente. A checagem Redis também é separada da gravação.

**Condição:** exige processamento concorrente da mesma transação, por exemplo sobreposição durante rebalanceamento/reprocessamento ou eventos equivalentes em partições diferentes. A entrega sequencial normal de uma única partição não demonstra essa falha. Aprovação e negação também usam listeners distintos e não compartilham uma transição atômica.

**Impacto:** débito/crédito repetidos ou liberação repetida da reserva, podendo deixar saldo/reserva negativos. O problema é diferente da duplicata sequencial documentada como corrigida em `SECURITY.md`.

**Evidência:** teste local executou o serviço real com duas chamadas simultâneas e repositórios substitutos que retornaram snapshots independentes de uma reserva aberta: ocorreram dois débitos e dois créditos. Isso demonstra a ausência de exclusão no serviço; a reprodução com transações PostgreSQL reais permanece necessária.

**Correção:** serializar a transição por transação, usando lock pessimista ou atualização condicional `settled=false` com conferência das linhas afetadas, dentro da mesma transação que movimenta os saldos. Tratar também a corrida de criação de marcador quando a reserva ainda não existe. Validar concorrência em PostgreSQL, incluindo aprovação versus negação.

### 2. Alta — gravação no banco e publicação no Kafka podem divergir permanentemente

**Local:** `transaction-service/src/main/java/com/fraudetection/transaction_service/services/TransactionService.java:66` e `kafka/producers/TransactionCreatedProducer.java:43`; padrão repetido em `FraudAnalysisService.java:57` e produtores de decisões.

O registro é confirmado antes da publicação. Uma interrupção entre essas etapas deixa a transação `CREATED` sem evento. Além disso, `KafkaTemplate.send` retorna um future que é ignorado: falhas assíncronas não impedem a resposta de sucesso nem o log “Published”. No antifraude, o consumidor pode marcar o evento como processado antes de saber se a decisão foi entregue.

**Impacto:** transações aceitas que nunca seguem para análise/liquidação; decisões persistidas que não chegam aos consumidores. Repetir a solicitação com a mesma chave recebe conflito, sem recuperar a publicação perdida. Isso não é o risco já documentado de uma liquidação recusada após receber uma aprovação.

**Correção:** transactional outbox no banco de cada produtor, com publicação recuperável e `eventId` estável. Consumidores devem deduplicar persistentemente. Aguardar o future melhora a detecção, mas sozinho não elimina a janela banco/broker.

**Validação:** análise do fluxo; falta teste de interrupção entre commit e publicação e de conclusão excepcional do future.

### 3. Alta — reentrega de evento pode impedir a recuperação da análise de fraude

**Local:** `fraud-detection-service/src/main/java/com/fraudetection/fraud_detection_service/services/FraudAnalysisService.java:43`, `entities/FraudAnalysis.java` (`transactionId` único) e `kafka/consumers/TransactionCreatedConsumer.java:32`.

`analyze` sempre cria uma nova análise. Se o processo parar depois de salvar e antes de publicar, ou antes de registrar a chave Redis, a reentrega tenta inserir novamente o mesmo `transactionId`. A constraint única rejeita a inserção antes da publicação da decisão já calculada. Não há caminho que carregue a análise existente e retome o trabalho. Perda/expiração das chaves Redis também expõe o problema.

**Impacto:** justamente a tentativa de recuperação falha repetidamente; a decisão pode nunca ser entregue e a reserva permanecer aberta. A constraint evita duplicar análises, mas não torna o processamento idempotente.

**Correção:** persistir análise e outbox na mesma transação; em reentrega, reconhecer a análise existente como resultado já produzido e recuperar sua entrega. Criar teste de reinício após persistência e antes da publicação.

**Validação:** fluxo e constraint confirmados no código; falha de infraestrutura não injetada nesta sessão.

### 4. Alta — contratos monetários permitem arredondamentos divergentes

**Local:** `transaction-service/src/main/java/com/fraudetection/transaction_service/dto/request/TransactionRequest.java:17`, `entities/Transaction.java:30`, `account-service/src/main/java/com/fraudetection/account_service/dto/request/PixDepositRequest.java:17` e entidades `Account`/`BalanceLock`.

As APIs validam apenas presença e positividade do valor. Aceitam frações inferiores a um centavo e valores acima da precisão de armazenamento. `Account` e `BalanceLock` declaram escala 4; `Transaction.amount` não declara precisão/escala e depende do mapeamento padrão (escala 2 no Hibernate disponível). As respostas de conta arredondam para duas casas. O ML exige até quatro casas. Não há normalização comum antes de salvar e publicar.

**Exemplo:** `1.2345` pode ser publicado a partir do objeto Java, reservado/liquidado com quatro casas e persistido na tabela de transações com duas. Valores com mais de quatro casas podem falhar no contrato ML; valores grandes podem chegar ao banco antes de serem rejeitados. A descrição OpenAPI de depósito “up to 2 decimals” não impõe validação.

**Evidência:** Bean Validation aceitou um depósito de `0.00001` sem violações. O código-fonte do Hibernate instalado confirma escala padrão 2. O esquema real de um banco já existente não foi inspecionado; `ddl-auto:update` exige verificar também eventuais esquemas legados.

**Correção:** definir unidade, precisão e escala únicas; rejeitar valores fora delas com Bean Validation e explicitar o mapeamento em todas as entidades. Se a regra do produto for BRL com duas casas, aplicá-la na entrada, nos eventos e na persistência. Testar igualdade entre valor solicitado, registro da transação, reserva, liquidação e extrato.

### 5. Alta — média histórica válida pode tornar inválida a requisição ao ML

**Local:** `fraud-detection-service/src/main/java/com/fraudetection/fraud_detection_service/services/FeatureEngineerService.java:40`, `repositories/FraudAnalysisRepository.java` e `ml-model-service/app/schemas.py:8`.

A média obtida com `AVG` é encaminhada sem normalização. Mesmo valores de entrada com duas casas podem produzir médias periódicas: `10.00`, `10.00`, `10.01` dão aproximadamente `10.003333…`. O contrato Python limita `avgAmountLast30Days` a quatro casas, portanto essa média não cabe nele.

O cliente trata erros de chamada com o mesmo fallback de indisponibilidade: score `0.50`, decisão `FLAGGED`. Logo, um histórico legítimo pode desativar a inferência normal sem o modelo estar fora do ar. Como a resolução de `FLAGGED` ainda não existe — limitação já conhecida — o efeito pode incluir saldo reservado indefinidamente.

**Correção:** normalizar a média conforme contrato explícito das features ou permitir precisão adequada para essa estatística; distinguir erro contratual HTTP 422 de indisponibilidade e monitorá-lo. Testar a integração Java/Python com médias não exatas.

**Validação:** incompatibilidade demonstrada pela consulta, encaminhamento e schema; teste HTTP não executado, pois as dependências Python não estão instaladas e Docker não estava acessível.

### 6. Média — extrato expõe dados técnicos e sinais de risco do remetente ao destinatário

**Local:** `ledger-service/src/main/java/com/fraudetection/ledger_service/dto/response/LedgerEntryResponse.java:23`, `repositories/LedgerEntryRepository.java` e `transaction-service/.../dto/event/TransactionCreatedPayload.java`.

O ledger seleciona eventos tanto pela conta de origem quanto pela de destino. O DTO público devolve o `eventPayload` integral. Assim, o dono da conta destinatária pode acessar `deviceId`, `ipAddress` quando preenchido e `idempotencyKey` enviados pelo remetente. Eventos de decisão também expõem score/motivo antifraude. O DTO ainda publica tópico e offset Kafka.

**Impacto:** a autorização da conta está correta, mas a autorização dos campos não está delimitada. Compartilhar uma transferência não justifica compartilhar identificadores técnicos do dispositivo da contraparte. Os scores expostos também permitem observar o comportamento do antifraude.

**Correção:** separar evento interno e representação pública; usar uma lista explícita de campos necessários ao extrato. Restringir sinais e justificativas internas a uma interface administrativa autorizada. Testar remetente e destinatário separadamente.

**Validação:** fluxo repositório → controller → DTO; não foi efetuado acesso HTTP a dados de usuários.

### 7. Média — IP controlado pelo cliente influencia a classificação de fraude

**Local:** `transaction-service/src/main/java/com/fraudetection/transaction_service/dto/request/TransactionRequest.java:23`, `services/TransactionService.java` e `fraud-detection-service/.../services/FeatureEngineerService.java:56`.

O IP vem do corpo da solicitação e é usado como sinal de risco. O cliente pode enviar `127.0.0.1`, omitir o campo ou enviar um valor inválido; todos são tratados como `isForeignIp=false`. IPv6 também cai no ramo falso. Além disso, a função classifica qualquer IPv4 público como “foreign”, sem informação geográfica.

**Impacto:** um usuário autenticado controla um sinal utilizado para decidir sua própria transação. Isso permite manipular features, embora não garanta aprovação: o modelo usa outros fatores. `docs/API.md` recomenda IP nulo ao navegador, mas não documenta essa fragilidade do sinal como risco.

**Correção:** obter o endereço em uma fronteira confiável; aceitar cabeçalhos de encaminhamento apenas de proxies configurados. Definir se o sinal representa geolocalização, reputação ou endereço público, tratar IPv6 e representar informação desconhecida sem convertê-la automaticamente em baixo risco. `deviceId` fornecido pelo cliente também não deve ser tratado como prova de dispositivo confiável.

### 8. Média — limite de memória do controle de login não é aplicado

**Local:** `auth-service/src/main/java/com/fraudetection/auth_service/services/LoginAttemptService.java:41`.

Quando o mapa atinge 10 mil entradas, o código remove apenas entradas expiradas. Ele continua inserindo identificadores novos mesmo quando nenhuma expirou. Após o limiar, cada falha também percorre o mapa inteiro para tentar limpá-lo.

**Impacto:** solicitações com muitos e-mails distintos permitem crescimento de memória e aumento do custo de CPU. É um problema específico adicional aos riscos de spraying, múltiplas réplicas e reinício já documentados. O alcance prático depende da taxa de requisições e dos recursos do servidor; não foi feito teste de carga.

**Evidência:** teste local com o serviço real registrou 10.001 identificadores não expirados e confirmou 10.001 entradas, apesar de `MAX_TRACKED_KEYS=10_000`.

**Correção:** estrutura com capacidade efetivamente limitada e expiração eficiente, junto de limitação de tráfego que não possa ser contornada trocando o e-mail. Se migrar para Redis, manter limites de cardinalidade e custo; mudar o armazenamento sozinho não resolve isso.

### 9. Média — ledger não preserva idempotência após falha parcial ou perda do Redis

**Local:** `ledger-service/src/main/java/com/fraudetection/ledger_service/services/LedgerEntryService.java:24`, `services/IdempotencyService.java:27` e consumidores.

Cada entrega cria um UUID novo para a entrada. A gravação MongoDB acontece antes da marca Redis, que expira em 24 horas. Se houver falha entre essas operações, perda do Redis ou replay após o TTL, o mesmo evento produz outra entrada. O documento não guarda `eventId` com unicidade. Tópico e offset não resolvem: além de não haver constraint, falta a partição para identificar um registro Kafka.

**Impacto:** extrato/auditoria duplicados. Este achado não afirma que a duplicação do ledger move dinheiro outra vez; o saldo pertence ao account-service.

**Correção:** usar `eventId` estável como identidade persistente/índice único e inserir de forma idempotente no MongoDB. Redis pode ser otimização, não a única garantia. Guardar também partição quando houver necessidade de rastreabilidade por coordenadas Kafka.

**Validação:** ordem das operações, identidade aleatória e TTL confirmados no código; teste com interrupção entre MongoDB e Redis pendente.

## Questões já documentadas, excluídas da contagem

- Kafka/Redis sem autenticação, depósito público simulado, ausência de compensação após falha de liquidação, JWT sem revogação imediata/audience e limitações gerais do throttling: `SECURITY.md`.
- Ausência de liberação/revisão de `FLAGGED`: checklist do `README.md`.
- Ausência de verificação de propriedade do e-mail e migração futura de `ddl-auto:update`: especificação de PIX.
- Modelo sintético e necessidade de confiar no artefato pickle: README do ML. O simples uso de pickle local não foi classificado como execução remota explorável.

A frase em `SECURITY.md` que descreve Kafka como não explorável por estar em localhost merece precisão: bind local limita o acesso externo aos ports publicados, mas os containers da mesma rede ainda alcançam o broker. Não é uma descoberta separada da falta de autenticação já registrada. A autenticação recomendada é dos serviços produtores/consumidores, não dos usuários finais.

## Boas práticas adicionais

Estas são melhorias de engenharia, separadas dos nove achados acima:

- `Transaction.type` e `status` não têm `@Enumerated(EnumType.STRING)`, acoplando a persistência à ordem dos enums. Tornar o contrato explícito e planejar migração dos dados antes de alterá-lo.
- Consultas de histórico/contagens por conta e período não possuem índices correspondentes declarados nas entidades; no ledger, também falta índice adequado ao filtro por conta e ordenação temporal. Verificar índices reais e planos de consulta antes de definir a migração.
- Clientes HTTP são construídos diretamente, sem timeouts explícitos na maioria deles. Centralizar configuração e definir limites de conexão/leitura. O circuit breaker do ML não substitui a configuração do transporte dos demais clientes.
- O CI executa serviços por caminho alterado, mas não há teste de contrato cruzado garantindo que eventos e schemas Java/Python continuam compatíveis. Os contratos monetários são um caso concreto que esse tipo de teste deve cobrir.

## Verificações e limitações

- `BalanceLockServiceTest`: **9 testes passaram**, sem falhas ou erros, em execução offline.
- Primeira tentativa teve erro de inicialização do Mockito devido ao mecanismo de attach restrito. Reexecução com o agente explícito funcionou, sem mudar o projeto:

  ```sh
  cd account-service
  ./mvnw -o -B -ntp test -Dtest=BalanceLockServiceTest \
    '-DargLine=-javaagent:/home/fabiano/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
  ```

- Provas locais adicionais: validação de depósito com cinco casas; concorrência no serviço com repositórios substitutos; cardinalidade do mapa de login. Fontes temporárias em `/tmp/FraudShieldAuditProbe.java` e `/tmp/FraudShieldLoginProbe.java` nesta sessão.
- Docker: acesso negado ao socket. Nenhum container foi criado, reiniciado ou alterado; nenhum saldo existente foi movimentado.
- Python: `pydantic` ausente no interpretador disponível; testes do ML não executados.
- Não foi executada a suíte completa nem um scanner de CVEs. Não foram atribuídas vulnerabilidades a versões de bibliotecas sem verificação específica.
- Alterações preexistentes do usuário foram preservadas. Os únicos arquivos de revisão persistentes adicionados por esta tarefa estão em `docs/reviews/`; as execuções Maven atualizaram artefatos em `target/`.

## Ordem sugerida de correção

1. Unificar os contratos monetários e corrigir a média enviada ao ML.
2. Garantir transição atômica de liquidação e testar concorrência em PostgreSQL.
3. Introduzir outbox e idempotência persistente, incluindo recuperação de análises e ledger.
4. Restringir campos públicos do extrato, corrigir a origem dos sinais de risco e limitar o armazenamento do throttling.

Resolver também o fluxo de `FLAGGED` já conhecido: sem ele, falhas recuperáveis do ML podem ter consequências permanentes sobre a disponibilidade do saldo.
