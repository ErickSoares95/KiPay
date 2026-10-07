# ADR-0001 — Movimentação de dinheiro concentrada no Ledger

**Status**: Aceita
**Data**: 2026-10

## Contexto

Uma transferência debita uma conta e credita outra. Se as duas contas vivessem em serviços com bancos
diferentes, o débito e o crédito não poderiam acontecer numa mesma transação ACID. Seria preciso uma saga para
mover o dinheiro em si, com uma janela em que o valor "saiu" de uma conta e ainda não "entrou" na outra,
e com compensações para cada falha.

Em sistemas financeiros, essa janela é exatamente o tipo de inconsistência que gera prejuízo e retrabalho
na conciliação.

## Decisão

Todas as contas contábeis e todos os lançamentos vivem no serviço **Ledger**. Uma liquidação debita a origem
e credita o destino **na mesma transação ACID**. Os demais serviços nunca alteram saldo; eles pedem ao Ledger.

A saga do serviço Transfers coordena os passos **ao redor** do dinheiro (validar contas, reservar, consultar
antifraude, liquidar ou liberar a reserva), mas a movimentação em si é sempre atômica.

## Alternativas consideradas

- **Saldo dentro do serviço Accounts, um banco por conta ou por titular** — exigiria saga para cada
  transferência, com janela de inconsistência e compensações complexas.
- **Transação distribuída (2PC)** — trava recursos, depende de um coordenador central e é evitada em
  microserviços.

## Consequências

- Positivas: saldo sempre consistente; liquidação simples de testar; auditoria centralizada.
- Negativas e riscos aceitos: o Ledger é um serviço crítico e precisa de alta disponibilidade; ele concentra
  carga de escrita. Se a escala exigir no futuro, o caminho é particionar o Ledger por conta, mantendo cada
  liquidação dentro de uma partição.
- Lição de arquitetura: a melhor forma de evitar uma saga é desenhar o bounded context certo.
