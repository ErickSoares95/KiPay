# ADR-0003 — Kafka como único broker de mensageria

**Status**: Aceita
**Data**: 2026-10-07

## Contexto

A versão 1.1.0 da constituição previa dois brokers: RabbitMQ para comandos e filas de trabalho desde a fase 1, e
Kafka a partir da fase de streaming (Pix), com a migração dos eventos de alto volume de um para o outro.

Isso significa operar, testar e monitorar duas tecnologias de mensageria, e fazer uma migração planejada de
eventos no meio do roadmap. Boa parte dos eventos previstos (`ContaAberta`, `ContaContabilCriada`,
`LancamentoRegistrado`) são fatos de domínio consumidos por vários serviços (Ledger, Accounts, Statement,
Notifications), e o read model do extrato (Statement) se beneficia de poder reprocessar eventos desde o início.

A mensageria entra no projeto na change 001 (abertura de conta), que precisa de eventos entre Accounts e Ledger.
A escolha do broker precisa ser feita antes dela.

## Decisão

O **Kafka** é o único broker de mensageria do projeto, desde a change 001. O RabbitMQ sai do stack. Eventos de
domínio e, a partir da fase de comunicação assíncrona, os comandos entre serviços trafegam por tópicos Kafka. A
publicação continua sendo feita via Outbox (Artigo VI), e todo consumidor continua idempotente (Artigo IV).

## Alternativas consideradas

- **RabbitMQ na fase 1 e Kafka a partir do streaming (plano anterior)**: dois brokers para operar e testar, e uma
  migração de eventos no meio do projeto, sem um problema descrito numa spec que exija os dois.
- **Somente RabbitMQ**: é mais simples de operar e traz retry, DLQ e filas de trabalho prontos. Retenção,
  reprocessamento e particionamento com ordenação por chave existem via Streams e Super Streams, mas são recursos
  mais recentes e menos adotados que no Kafka, que é construído em torno de um log retido. Como a maior parte da
  comunicação assíncrona do projeto são eventos de domínio com vários consumidores, e o extrato depende de
  reprocessamento, o Kafka atende melhor.

## Consequências

- Positivas:
  - Um único broker para subir no Docker Compose, testar com Testcontainers e observar.
  - Eventos ficam retidos no tópico: um read model novo ou corrigido (Statement) pode ser reconstruído
    reprocessando os eventos.
  - Ordenação garantida por chave de partição (por exemplo, `aggregateId`), útil para eventos de uma mesma conta.
  - A migração prevista para a fase de streaming deixa de existir.
- Negativas e riscos aceitos:
  - Kafka é mais pesado de operar localmente que o RabbitMQ; usamos um único broker em modo KRaft (sem ZooKeeper)
    no ambiente local.
  - Kafka não tem filas de trabalho nem DLQ nativas: retry e mensagens com falha passam a ser tratados com tópicos
    de retry e um dead letter topic (DLT), configurados na aplicação.
  - Comandos ponto a ponto (Transfers → Ledger) ficam menos naturais que numa fila; a modelagem desses tópicos e
    das respostas fica para o design da change que introduzir comandos assíncronos.
  - Os conceitos de filas e roteamento por exchange deixam de ser praticados no projeto.
