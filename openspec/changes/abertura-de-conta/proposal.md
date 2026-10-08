# Proposal

## Why

Hoje não existe nenhuma conta na carteira digital, e todas as features seguintes dependem dela: depósito,
transferência, extrato e notificações. A abertura de conta também é a primeira operação que atravessa dois serviços,
Accounts e Ledger. Ela inaugura na prática os princípios de um banco por serviço, consistência eventual por eventos,
Outbox, consumidor idempotente e unicidade de CPF (`docs/arquitetura/visao-geral.md`, seções 3, 4 e 5).

## What Changes

- Uma pessoa física autenticada, com 18 anos ou mais, pode pedir a abertura de uma conta informando nome completo, CPF
  e data de nascimento. O e-mail vem da identidade autenticada e não é pedido no cadastro (LGPD: coletar só o
  necessário).
- Cada identidade fica ligada a um único CPF, e cada CPF a uma única identidade.
- A conta nasce com status PENDENTE (`PENDING`) no Accounts, que registra o evento `AccountOpened` na mesma
  transação, via Outbox.
- Ao consumir `AccountOpened`, o Ledger cria a conta contábil (`LedgerAccount`) correspondente e registra o evento
  `LedgerAccountCreated`, também via Outbox.
- Ao consumir `LedgerAccountCreated`, o Accounts muda a conta para ATIVA (`ACTIVE`). Só a partir daí a conta pode
  movimentar dinheiro.
- Cada CPF tem no máximo uma conta não encerrada: uma segunda abertura com o mesmo CPF é recusada com um erro de
  negócio estável. Depois do encerramento, o CPF pode abrir uma nova conta, e os dados da conta encerrada são mantidos.
- Uma conta que não ativa continua PENDENTE, sem cancelamento automático. As contas PENDENTE há mais de 10 minutos
  ficam visíveis numa métrica.
- A requisição de abertura exige chave de idempotência (`Idempotency-Key`). A mesma chave devolve o mesmo resultado,
  sem criar uma segunda conta.
- O titular consulta a própria conta, incluindo o status, e não enxerga contas de outros titulares.
- Os serviços Accounts e Ledger nascem com banco PostgreSQL próprio, Kafka, Docker Compose, CI e observabilidade de base
  (logs JSON mascarados, `traceId` propagado também pelos eventos, métricas RED e health checks).
- **Antecipação em relação ao roadmap**: o Keycloak entra já nesta change. Accounts e Ledger validam o token JWT, e a
  conta fica vinculada à identidade de quem a abriu (Artigo IX). O API Gateway continua na feature 3 (borda e
  identidade). A escolha do Keycloak e a antecipação ficam registradas na ADR-0005, e a seção 6 de
  `docs/arquitetura/visao-geral.md` será atualizada para refletir isso.

## Capabilities

### New Capabilities

- `contas`: cadastro do titular e ciclo de vida da conta no Accounts. Cobre a abertura, os dados obrigatórios e a
  idade mínima, a unicidade de CPF, o vínculo entre identidade e CPF, a idempotência da abertura, a ativação após a
  confirmação do Ledger, a visibilidade de contas pendentes, a consulta da própria conta e a regra de que conta não
  ATIVA não movimenta dinheiro.
- `contas-contabeis`: criação, no Ledger, da conta contábil de cada conta aberta, de forma idempotente, e confirmação
  ao Accounts.

### Modified Capabilities

Nenhuma. Ainda não existem specs em `openspec/specs/`.

## Decisões de negócio

Respondidas em 2026-10-07, durante a revisão da proposta:

1. **Idade mínima**: 18 anos completos na data do pedido. Conta para menor de idade fica fora de escopo.
2. **Contas por CPF**: no máximo uma conta não encerrada por CPF. Depois do encerramento, o mesmo CPF pode abrir uma
   nova conta, e os dados da conta encerrada são mantidos.
3. **Dados obrigatórios**: nome completo, CPF e data de nascimento. O e-mail vem da identidade autenticada e não é
   pedido no cadastro. Ele é guardado junto com o titular, e um pedido de uma identidade sem e-mail é recusado.
4. **Ativação que não acontece**: a conta continua PENDENTE, sem cancelamento automático nesta change. As contas
   PENDENTE há mais de 10 minutos ficam visíveis numa métrica.
5. **Identidade e CPF**: uma identidade só pode abrir conta para um único CPF, e um CPF fica ligado a uma única
   identidade.
6. **Validação do CPF**: formato e dígitos verificadores. Verificar se o CPF está ativo na Receita Federal fica fora de
   escopo.

## Fora de escopo

- Depósito, transferência e qualquer movimentação de dinheiro. As operações que vão recusar movimentação de conta não
  ATIVA nascem nas changes de depósito e de transferência.
- Bloqueio (`BLOCKED`) e encerramento (`CLOSED`) de conta. A regra de reabertura depois do encerramento entra nesta
  change, mas o fluxo de encerramento não.
- Conta para menor de 18 anos, com ou sem responsável.
- Cancelamento automático de conta que não ativa.
- API Gateway, Service Registry e Config Server (feature 3).
- Cadastro de usuário no Keycloak pelo próprio sistema (self-signup). Nesta change, os usuários de teste ficam no realm
  importado no Docker Compose.
- KYC completo: verificação de documento com foto, prova de vida, consulta da situação do CPF na Receita Federal e
  listas restritivas.
- Limites por perfil de conta e antifraude.
- Extrato e notificação de conta aberta (Statement e Notifications).
- Exportação de traces para um coletor e dashboards (feature 8). Nesta change, o `traceId` é propagado e aparece nos
  logs.

## Impact

- **Novos serviços**: `services/accounts` e `services/ledger`, previstos na visão geral e na ADR-0002, com um POM raiz
  agregador.
- **APIs**: `POST /accounts` (corpo com nome completo, CPF e data de nascimento) e `GET /accounts/{accountId}` no Accounts, documentadas em OpenAPI. Os erros seguem o
  formato `ProblemDetail`.
- **Eventos**: `AccountOpened` (Accounts → Ledger) e `LedgerAccountCreated` (Ledger → Accounts), com schema
  versionado.
- **Infraestrutura**: `infra/docker-compose.yml` com dois PostgreSQL, Kafka (KRaft) e Keycloak com realm importado;
  pipeline de CI executando `mvn verify`.
- **Documentação**: atualização do roadmap na visão geral (Keycloak antecipado) e do glossário com os termos novos
  desta change. Duas ADRs novas: ADR-0005 (Keycloak como provedor de identidade, antecipado para esta change) e
  ADR-0006 (compatibilidade do Spring Cloud com o Boot 4.1 e ferramenta de testes de contrato).
