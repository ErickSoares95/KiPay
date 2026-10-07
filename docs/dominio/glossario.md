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
