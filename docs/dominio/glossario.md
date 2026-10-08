# Glossário pt-BR → inglês

> Correspondência entre os termos de negócio usados na documentação (em português) e os nomes usados no código
> (em inglês), conforme a ADR-0004. Um termo novo entra aqui antes de virar código.
> O significado de cada termo está em `docs/arquitetura/visao-geral.md` (seção 2) e em `conceitos-carteira-digital.md`.

## Conceitos

| Português (docs) | Inglês (código) |
|---|---|
| Titular | `AccountHolder` |
| Conta | `Account` |
| Conta contábil | `LedgerAccount` |
| Livro-razão | `Ledger` |
| Lançamento | `LedgerEntry` |
| Débito / Crédito | `DEBIT` / `CREDIT` |
| Partidas dobradas | double-entry |
| Saldo contábil | `LedgerBalance` |
| Saldo disponível | `AvailableBalance` |
| Reserva | `Hold` |
| Liquidação | `Settlement` |
| Estorno | `Reversal` |
| Bloqueio cautelar | `PrecautionaryBlock` |
| Disponibilidades (conta da empresa) | `Cash` |
| Depósito (cash-in) | `Deposit` |
| Transferência | `Transfer` |
| Extrato | `Statement` |
| Notificação | `Notification` |
| Conciliação | `Reconciliation` |
| Chave de idempotência | `IdempotencyKey` (header HTTP `Idempotency-Key`) |
| Dinheiro / valor monetário | `Money` |
| Abertura de conta | `AccountOpening` |
| Pedido de abertura (corpo do `POST /accounts`) | `AccountOpeningRequest` |
| Ativação (da conta) | `activation` (método `Account.activate`) |
| Encerramento (da conta) | `closing` (método `Account.close`) |
| Conta pendente além do limite | stale pending account |
| Conta desconhecida | `UnknownAccount` (`UnknownAccountException`) |
| Política do titular (regras de aceitação, como a idade mínima) | `AccountHolderPolicy` |
| CPF mascarado | `masked` (`Cpf.masked()`) |
| Dígitos verificadores (do CPF) | check digits |
| Evento processado | `ProcessedEvent` |
| Evento do Outbox | `OutboxEvent` |
| Registro de idempotência | `IdempotencyRecord` |
| Reabertura (nova conta depois do encerramento) | `reopening` |
| Separar para análise (mensagem que não pode ser processada) | dead letter topic (DLT), sufixo `.dlt` no tópico |

## Atributos

| Português (docs) | Inglês (código) |
|---|---|
| Pode movimentar | `canMoveMoney` |
| Identificador da conta | `accountId` |
| Identificador da conta contábil | `ledgerAccountId` |
| Identidade do titular | `ownerSubject` |
| Nome completo | `fullName` |
| Data de nascimento | `birthDate` |
| E-mail | `email` |
| Momento da abertura | `openedAt` |
| Momento da ativação | `activatedAt` |
| Moeda | `currency` |
| Idade mínima | `minimumAge` |

## Métricas

| Português (docs) | Inglês (código) |
|---|---|
| Contas pendentes além do limite | `accounts.pending.stale` |
| Eventos não publicados | `outbox.pending` |

## Códigos de erro

Valores da propriedade `code` do `ProblemDetail` (constituição, Artigo XI).

| Português (docs) | Inglês (código) |
|---|---|
| Dados inválidos | `VALIDATION_ERROR` |
| CPF inválido | `ACCOUNT_INVALID_CPF` |
| Titular menor de idade | `ACCOUNT_HOLDER_UNDERAGE` |
| Identidade sem e-mail | `IDENTITY_EMAIL_MISSING` |
| Conta já aberta | `ACCOUNT_ALREADY_OPEN` |
| CPF já cadastrado | `ACCOUNT_CPF_ALREADY_REGISTERED` |
| Identidade já vinculada a outro CPF | `ACCOUNT_IDENTITY_ALREADY_LINKED` |
| Conta não encontrada | `ACCOUNT_NOT_FOUND` |
| Chave de idempotência ausente | `IDEMPOTENCY_KEY_MISSING` |
| Chave de idempotência inválida | `IDEMPOTENCY_KEY_INVALID` |
| Chave de idempotência reutilizada | `IDEMPOTENCY_KEY_REUSED` |
| Pedido em processamento | `IDEMPOTENCY_REQUEST_IN_PROGRESS` |
| Não autenticado | `AUTHENTICATION_REQUIRED` |
| Serviço temporariamente indisponível | `SERVICE_UNAVAILABLE` |

## Serviços

| Português (docs) | Inglês (código, `services/<nome>`) |
|---|---|
| Accounts | `accounts` |
| Ledger | `ledger` |
| Transfers | `transfers` |
| Statement | `statement` |
| Notifications | `notifications` |
| Antifraude | `antifraud` |
| Pix | `pix` |
| Reconciliation | `reconciliation` |
| Assistant | `assistant` |

## Status da conta

| Português (docs) | Inglês (código) |
|---|---|
| PENDENTE | `PENDING` |
| ATIVA | `ACTIVE` |
| BLOQUEADA | `BLOCKED` |
| ENCERRADA | `CLOSED` |

## Estados da transferência

| Português (docs) | Inglês (código) |
|---|---|
| CRIADA | `CREATED` |
| RESERVADA | `HELD` |
| EM_ANALISE | `UNDER_REVIEW` |
| LIQUIDADA | `SETTLED` |
| RECUSADA | `REJECTED` |
| ESTORNADA | `REVERSED` |

## Eventos

| Português (docs) | Inglês (código) |
|---|---|
| Conta aberta | `AccountOpened` |
| Conta contábil criada | `LedgerAccountCreated` |
| Lançamento registrado | `LedgerEntryPosted` |

## Siglas mantidas no original

Pix, CPF, SPI, DICT, MED e LGPD não são traduzidas. No código seguem o padrão de nomes do Java (`PixKey`, `Cpf`).
