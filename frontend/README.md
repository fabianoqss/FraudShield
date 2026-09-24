# FraudShield — frontend

Interface em português para pagamentos e detecção de fraude simulados. React, Vite, TypeScript strict, React Router e CSS simples. As chamadas usam `fetch` por meio do cliente em `src/api/client.ts`.

## Instalar e executar

Use Node.js 22.12 ou superior e npm. Dentro de `frontend/`:

```bash
npm ci
npm run dev
```

Acesse http://127.0.0.1:5173. O `package-lock.json` registra as versões das dependências.

## Backend necessário

Mantenha o backend em execução com infraestrutura (PostgreSQL, MongoDB, Kafka e Redis), auth-service, account-service, transaction-service, fraud-detection-service, ml-model-service, ledger-service e api-gateway na porta 8080. O notification-service completa o fluxo de notificações do backend. A inicialização e as variáveis de ambiente estão documentadas no README do backend.

O proxy do Vite encaminha `/auth`, `/accounts`, `/transactions` e `/ledger` para `http://localhost:8080`. Não há mock server e nenhuma chamada da aplicação usa diretamente portas dos serviços.

O contrato é `../docs/API.md`. Endpoints ainda não disponíveis mostram erro na tela com possibilidade de nova consulta; a interface não fabrica dados para substituí-los.

## Comandos de validação

```bash
npm run build
npm test
npm run typecheck
npm run test:watch
```

O build gera `dist/`. Para publicar esse diretório, configure no servidor o encaminhamento das quatro famílias de rotas da API ao gateway e o fallback para `index.html` nas rotas da interface. O proxy do Vite vale apenas durante o desenvolvimento.

## Funcionalidades

- Cadastro com erros por campo; login e aviso de bloqueio por 15 minutos no HTTP 429.
- Rotas protegidas e restauração da sessão ao recarregar a página.
- Listagem/seleção de contas, criação quando não há contas, saldo total, bloqueado e disponível, cópia do identificador.
- Minhas chaves PIX (`/chaves`): lista por conta selecionada, cadastro de CPF/e-mail do usuário ou chave aleatória, cópia e remoção com confirmação; limite de 5 chaves.
- Depósito PIX simulado por chave cadastrada (e-mail, CPF ou aleatória); chave não cadastrada retorna aviso específico.
- Transferências PIX, crédito ou débito por consulta de chave → confirmação do nome e CPF mascarado, com acompanhamento a cada 1,5 segundo por até 30 segundos.
- Extrato paginado com origem/destino e status, detalhes da transação e eventos de auditoria com pontuação de fraude.
- Troca de senha seguida de logout e logout explícito.

## Autenticação e reenvios

O access token fica somente em memória; o refresh token fica em `sessionStorage`. Nome e identificador do usuário vêm dos claims `fullName` e `sub` do JWT. Decodificar o JWT serve à interface; a autorização é feita pelo backend.

O cliente compartilha uma única promessa de refresh entre chamadas concorrentes, incluindo restauração de sessão. Após HTTP 401, repete a chamada autenticada no máximo uma vez. Um 401 atrasado usa o access token já renovado. Falha no refresh limpa a sessão e leva ao login; o refresh antigo nunca é reenviado automaticamente.

Um HTTP 401 na troca de senha também pode significar senha atual incorreta. Após uma renovação e uma repetição, esse erro é mostrado no formulário sem encerrar uma sessão válida. O logout aguarda um refresh em andamento para revogar o token mais recente.

Cada novo envio de transferência recebe um UUID. Em falha de rede ou resposta ilegível, os dados ficam bloqueados no formulário e a ação “Repetir o mesmo envio” reutiliza exatamente o payload e a chave originais. Sair da página descarta esse envio pendente; consulte o extrato antes de iniciar outra transferência. O `deviceId` é criado uma vez em `localStorage`; `ipAddress` é enviado como `null`.

## Decisões e limites do contrato

- O cadastro de chave envia somente `{ type }`; o servidor fornece o valor. CPF e e-mail já presentes na conta ficam desabilitados. Podem existir várias chaves aleatórias distintas, respeitando o limite total de 5.
- A chave consultada vai somente no corpo de `POST /accounts/pix-keys/lookup`. O formulário não recebe nem armazena identificador da conta de destino: confirma nome/CPF mascarado e envia `lookupId` em `POST /transactions`, para todos os tipos de pagamento.
- O `lookupId` vale por 5 minutos. A tela usa `expiresAt` para voltar à consulta ao expirar, preservando chave e valor; uma rejeição de consulta expirada/inválida no servidor faz o mesmo. Voltar ou iniciar nova transferência exige nova consulta.
- Em HTTP 429 na consulta, o botão fica bloqueado pela quantidade de segundos de `Retry-After`, com contagem regressiva. Se o header estiver ausente ou inválido, o fallback é 60 segundos (janela documentada na spec).
- Repetir um envio incerto mantém o payload original, inclusive `lookupId`, mesmo se a consulta já expirou: o backend verifica idempotência antes da consulta. Não se substitui silenciosamente uma consulta em um envio pendente.
- O contrato mantém `destinationAccountId` nas respostas de transações e no histórico. O extrato existente continua usando esse campo; a consulta e o payload de transferência não o usam.
- Os diferentes erros 422 não têm código estruturado. A identificação de consulta inválida/expirada e mesma conta usa as mensagens documentadas do backend; erros não reconhecidos preservam `message`.

- Repetir uma chave já aceita retorna 409, sem recuperar o ID original. A interface orienta consultar o extrato e não declara que a primeira tentativa falhou.
- O fim dos 30 segundos de polling significa análise pendente, não rejeição. Navegar para outra tela cancela o acompanhamento.
- `FLAGGED` aparece como “EM REVISÃO” e mantém o valor reservado. Não há revisão manual implementada no backend.
- O ledger não oferece filtro por transação no servidor. Cada página consultada é filtrada localmente por `transactionId`; se houver outras páginas, a interface oferece “Buscar nas próximas páginas”. Uma busca parcial não é apresentada como ausência definitiva de eventos.
- Depósitos não constam no extrato de transferências nem no ledger.
- Saldos, status e eventos são atualizados de forma assíncrona. As telas permitem atualizar os dados.
- Datas sem offset de fuso no contrato são exibidas conforme a interpretação local do navegador; o contrato não define um fuso para esses valores.
- Mensagens estruturadas do backend (`message` e `fieldErrors`) são preservadas; elas podem estar em inglês. Rótulos, orientações e mensagens próprias da interface estão em português.

## Testes

Vitest + Testing Library em jsdom cobrem renovação single-flight, 401 atrasado, limite de repetição, falha de refresh, restauração, logout concorrente, JWT com acentos, respostas 204 e erros não JSON, cadastro/login, troca de senha, cadastro/listagem/remoção de chaves, limite de 5, depósito por chave cadastrada, consulta e confirmação de transferência, expiração local e HTTP 422, espera de Retry-After, erros 404/409/503, idempotência após falha de rede, polling, cancelamento e paginação do ledger.

Os testes substituem o transporte HTTP ou métodos do cliente dentro do processo de testes. Não iniciam servidor simulado. A validação automatizada não substitui um teste integrado com todos os serviços reais em execução.
