# FraudShield — frontend

Base em React, Vite e TypeScript strict, com React Router, CSS simples e testes em Vitest + Testing Library (jsdom).

## Instalar e executar

Use Node.js 22.12 ou superior e npm. Dentro de `frontend/`:

```bash
npm install
npm run dev
```

Acesse http://127.0.0.1:5173. A instalação inicial gera `package-lock.json`; ele deve ser versionado após uma instalação bem-sucedida. Nas instalações seguintes, use `npm ci`.

## Comandos

```bash
npm run build
npm test
npm run typecheck
npm run test:watch
```

O build gera `dist/`. O teste inicial verifica a navegação de uma URL desconhecida de volta à página inicial.

## Integração prevista

O proxy de desenvolvimento encaminha `/auth`, `/accounts`, `/transactions` e `/ledger` para `http://localhost:8080`. As chamadas futuras devem usar caminhos relativos e seguir `../docs/API.md`.

Para os fluxos integrados, mantenha a infraestrutura e os serviços do backend em execução: bancos, Kafka, Redis, autenticação, contas, transações, detecção de fraude, modelo ML, ledger e gateway na porta 8080. Consulte o README do backend para inicialização. A página inicial atual pode ser aberta sem o backend.

O proxy é exclusivo do servidor de desenvolvimento. Uma publicação de `dist/` precisará de roteamento equivalente para a API e fallback para `index.html` nas rotas da interface.

## Estado atual

Esta etapa prepara dependências, configurações e a página inicial. Autenticação, telas de conta, depósito, transferência, extrato e seus testes serão implementados nas próximas etapas. Não há mock server.

A instalação no ambiente de criação foi impedida por erro de DNS (`EAI_AGAIN` ao acessar registry.npmjs.org). As versões declaradas ainda precisam ser resolvidas pelo npm; build, testes e verificação de tipos dependem dessa instalação.
