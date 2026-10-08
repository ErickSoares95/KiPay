# Design

## Context

O repositório ainda não tem código. `services/` e `infra/` estão vazios. Esta change cria os dois primeiros serviços,
`accounts` e `ledger`, como projetos Maven independentes, e um `pom.xml` raiz que só agrega os módulos (ADR-0002). A
mensageria é o Kafka (ADR-0003), e o código usa os nomes em inglês do glossário (ADR-0004). A arquitetura
interna de cada serviço está em D1 (ADR-0008). A motivação está em
`proposal.md` (Why) e os requisitos em `specs/contas/spec.md` e `specs/contas-contabeis/spec.md`.

Por decisão tomada no chat durante a criação desta change, o Keycloak entra já aqui. Assim o Accounts, único serviço
que recebe requisições com token nesta change, cumpre a parte do Artigo IX que cabe a ele (validar o token e autorizar
por recurso). O Ledger só consome eventos e não tem API de negócio, então não recebe token. Ele passa a ser resource
server quando expuser API (change 002).

O API Gateway continua na feature 3. A ADR-0005 registra a escolha do Keycloak, a antecipação, o escopo da validação
por serviço e o adiamento da validação no Gateway. As decisões de negócio estão na seção "Decisões de negócio" da
proposta. A revisão de 2026-10-08 ajustou a segurança do Ledger, a concorrência da idempotência e o enxugamento das
tarefas.

### Verificação contra a constituição

| Artigo | Como este design atende | Conflito |
|---|---|---|
| I. Um banco por serviço | `accounts-db` e `ledger-db` separados. Integração só pelos eventos `AccountOpened` e `LedgerAccountCreated` | Não |
| II. Dinheiro não é ponto flutuante | Esta change não tem valores. A conta contábil guarda só a moeda (`BRL`). `Money` nasce na change de depósito | Não |
| III. Livro-razão imutável | `LedgerAccount` não tem coluna de saldo; o saldo será derivado dos `LedgerEntry` (change 002) | Não |
| IV. Idempotência | `Idempotency-Key` obrigatório no `POST /accounts`. Consumidores com tabela `processed_events` e restrições de unicidade | Não |
| V. Consistência declarada | Seção "Decisões de consistência" nas duas specs | Não |
| VI. Outbox | Tabela `outbox_events` em cada serviço, gravada na mesma transação; o envelope traz `eventId`, `occurredAt`, `aggregateId` e `schemaVersion` | Não |
| VII. Testes | Unitários com `@DisplayName`, integração com Testcontainers (PostgreSQL e Kafka) e testes de contrato dos eventos | Não. A ferramenta de contrato pede ADR (ADR-0006, ver D10). Um teste ArchUnit protege o domínio do Ledger (ADR-0008, D1) |
| VIII. Observabilidade | Logs estruturados, `traceId` propagado por HTTP e Kafka (atravessando o Outbox), métricas RED, health checks | Não |
| IX. Segurança | O Accounts valida o JWT do Keycloak (issuer e audiência). A conta é vinculada ao `sub` do token, e o CPF sai mascarado. O Ledger não recebe requisições com token nesta change: só consome eventos, e o Actuator fica numa porta de management fora da rede pública (D12). Só o `/v3/api-docs/**` do Accounts é público (D3) | **Parcial e aceito na ADR-0005**: a validação no Gateway chega na feature 3, e a do Ledger quando ele expuser API (change 002). A proteção do consumidor Kafka (ACL no broker) fica para uma fase futura |
| X. Simplicidade | Só os dois serviços previstos. Tecnologia nova: Keycloak (já no stack, antecipado, ADR-0005) e a ferramenta de contrato (ADR-0006) | Não |
| XI. Erros padronizados | `ProblemDetail` com a propriedade `code` | Não |
| XII. APIs atuais | Starters `-webmvc` e `-security-oauth2-resource-server`, `SecurityFilterChain` com lambdas, Jackson 3 (`tools.jackson.*`), `jakarta.*`, `@MockitoBean`, `CompletableFuture`, `logging.structured.format.console` | **Exceção aceita na ADR-0006**: springdoc e Pact ficam fora dos BOMs, com versão fixada na ADR. **Exceção aceita na ADR-0008**: o ArchUnit (escopo de teste) fica fora dos BOMs, com versão fixada na ADR |

## Goals / Non-Goals

**Goals:**
- Deixar os dois serviços rodando ponta a ponta no Docker Compose: abertura, criação da conta contábil e ativação.
- Deixar prontos e testados os blocos que as próximas changes vão reaproveitar em cada serviço: Outbox, consumidor
  idempotente, envelope de eventos, `ProblemDetail`, segurança de resource server (no Accounts) e observabilidade.
- Fazer cada cenário das specs corresponder a pelo menos um teste automatizado.

**Non-Goals:**
- Biblioteca compartilhada entre serviços. O envelope de eventos e o Outbox são duplicados em cada serviço (ver D11).
- Publicar um evento `AccountActivated`. Ninguém consome esse evento ainda, e ele nasce com o Statement ou o
  Notifications.
- Limpeza automática de `idempotency_records`, `processed_events` e `outbox_events` já publicados.
- Testes automatizados contra um Keycloak real. Os testes usam JWTs de teste, e o Keycloak real é verificado no smoke
  test do Compose.

## Decisions

### D1. Estrutura do repositório e dos serviços

```
pom.xml                         (packaging pom, só <modules>)
services/accounts/              io.github.ericksoares95.kipay.accounts
services/ledger/                io.github.ericksoares95.kipay.ledger
infra/docker-compose.yml
infra/keycloak/kipay-realm.json
.github/workflows/ci.yml
```

Cada serviço herda de `spring-boot-starter-parent` 4.1.x, com Java 25 e `spring.threads.virtual.enabled=true`.

**Arquitetura interna (ADR-0008).** A estrutura segue a importância do subdomínio:

- **Accounts (subdomínio de suporte)**: pacotes por funcionalidade, com entidades ricas: `account`, `outbox`,
  `idempotency`, `messaging`, `web` (erros e segurança).
- **Ledger (domínio principal)**: Arquitetura Hexagonal. O Transfers seguirá o mesmo modelo quando for criado.

```
ledger/
  domain/                    Java puro (sem Spring nem JPA): LedgerAccount, Currency, evento LedgerAccountCreated
  application/port/in/       CreateLedgerAccountUseCase
  application/port/out/      LedgerAccountRepository, ProcessedEvents, DomainEventPublisher, IdGenerator
  application/service/       CreateLedgerAccountService (sem @Service; transação e idempotência)
  adapter/in/messaging/      AccountOpenedListener, DefaultErrorHandler e DLT
  adapter/out/persistence/   JdbcClient (ON CONFLICT), processed_events, UuidV7 (implementa IdGenerator)
  adapter/out/outbox/        OutboxWriter (implementa DomainEventPublisher), OutboxRelay, envelope
  config/                    registra os serviços de aplicação como @Bean
```

- **Dependências permitidas**, verificadas por um teste ArchUnit (tarefa 6.1), que falha se alguma for violada:
  - `domain` só depende do JDK e do próprio `domain`. Em especial, não depende de Spring, de `jakarta.persistence` nem de
    `jakarta.transaction`;
  - `application` só depende do JDK, de `domain`, de `application` e de `org.springframework.transaction..`
    (`@Transactional`, aceito na ADR-0008). Não depende de `adapter` nem de JPA;
  - `adapter` pode depender das demais camadas.
- O `CreateLedgerAccountService` não usa `@Service`, que é do Spring fora de `org.springframework.transaction..`. Ele é
  registrado como `@Bean` em `config/`, e o `@Transactional` fica na classe.
- O id da `LedgerAccount` vem da porta `IdGenerator`, implementada pelo `UuidV7` (D2) em `adapter/out/persistence`. O
  domínio e a aplicação não conhecem a versão do UUID. Os testes unitários do serviço usam um `IdGenerator` falso, com
  UUIDs conhecidos.
- Entidades JPA do Ledger, quando houver leitura (change 002), serão classes de persistência separadas do domínio, em
  `adapter/out/persistence`. Nesta change o insert é nativo (`JdbcClient`) e não há entidade JPA.
- Alternativa descartada: **Arquitetura Hexagonal em todos os serviços**. O Accounts é subdomínio de suporte, com regras
  simples (CPF, idade, vínculo). Portas e adaptadores só acrescentariam classes e mapeamentos, sem proteger um domínio
  complexo (Artigo X).
- Alternativa descartada: **camadas tradicionais (controller, service, repository) no Ledger**. Deixam o domínio
  acoplado ao JPA e ao Spring, e nada impede essa dependência de crescer. No Ledger, o domínio é o núcleo do produto, e
  as regras dos Artigos III e IV precisam de testes sem Spring.

Starters nos dois serviços: `spring-boot-starter-webmvc` (para o Actuator), `-data-jpa`, `-flyway`, `-kafka`,
`-actuator` e `-opentelemetry`, mais `micrometer-registry-prometheus`. Só no Accounts: `-validation`,
`-security-oauth2-resource-server` e `springdoc-openapi-starter-webmvc-ui`, cuja versão fica fora dos BOMs e é
registrada na ADR-0006.

Testes: `spring-boot-starter-test`, `spring-boot-testcontainers` e os módulos de PostgreSQL e Kafka do Testcontainers
nos dois serviços, e `spring-security-test` só no Accounts.

- Alternativa descartada: **Spring Data JDBC**. É mais simples, mas o JPA é o padrão mais conhecido e atende bem os
  dois agregados. As consultas específicas (`FOR UPDATE SKIP LOCKED`, `ON CONFLICT DO NOTHING`) usam `JdbcClient`.

### D2. Modelo de dados do Accounts

- Identificadores: todas as chaves primárias geradas pelo sistema (`account_holders`, `accounts`, `outbox_events`) são
  **UUID v7**, gerados na aplicação. O UUID v7 é ordenável no tempo, o que mantém o índice B-tree compacto, ao
  contrário do v4 aleatório. Um gerador único em cada serviço (a classe `UuidV7`, escrita sobre `java.util.UUID`, sem
  biblioteca fora dos BOMs) serve aos dois caminhos de gravação: as entidades JPA (via `@IdGeneratorType`) e os
  inserts nativos com `JdbcClient` (`outbox_events` e, no Ledger, `ledger_accounts` com `ON CONFLICT`). No Ledger, o
  `UuidV7` implementa a porta `IdGenerator` (D1). A `Idempotency-Key` do cliente aceita qualquer versão de UUID.
- `account_holders`: `id` (UUID v7), `cpf` (CHAR(11), **UNIQUE**, constraint `uk_account_holders_cpf`),
  `owner_subject` (o `sub` do JWT, **UNIQUE**, constraint `uk_account_holders_owner_subject`), `full_name`
  (VARCHAR(200)), `birth_date` (DATE), `email` (VARCHAR(320), do claim `email` do token) e `created_at`. Esse par de
  restrições garante o vínculo 1:1 entre identidade e CPF.
- `accounts`: `id` (UUID v7), `account_holder_id` (FK), `status` (`PENDING`, `ACTIVE`, `CLOSED`), `opened_at`,
  `activated_at` e `version` (lock otimista). O índice único parcial `uk_accounts_open_per_holder` em
  `account_holder_id` `WHERE status <> 'CLOSED'` garante no máximo uma conta não encerrada por titular, e portanto por
  CPF.
- A reabertura depois do encerramento cria uma nova `Account` para o mesmo `AccountHolder`, e a conta `CLOSED` fica
  intacta. O valor `CLOSED` já existe no enum e na constraint para que a regra seja testada, mas nenhuma transição para
  ele é implementada nesta change (os testes inserem a conta encerrada direto no banco).
- O CPF é um value object `Cpf`: normaliza para 11 dígitos, valida o formato e os dígitos verificadores e rejeita
  dígitos repetidos. `toString()` devolve o CPF mascarado (`***.456.789-**`), e `masked()` é usado nas respostas.
- A idade mínima fica no `AccountHolderPolicy`, que recebe um `Clock` injetado e calcula a data civil no fuso
  `America/Sao_Paulo`. Quem completa 18 anos no dia do pedido é aceito. O limite de 18 anos é uma constante de domínio,
  não uma configuração.
- O nome completo é guardado como informado, sem os espaços nas pontas. Um nome em branco é inválido.
- `Account.canMoveMoney()` devolve `true` somente para `ACTIVE`. `Account.activate(Instant)` é idempotente: numa conta
  já `ACTIVE`, não muda nada.
- Alternativa descartada: **uma só tabela `accounts` com o CPF**. Ela mistura Titular e Conta, que são conceitos
  distintos no glossário, e duplicaria os dados do titular a cada reabertura.
- Alternativa descartada: **unicidade de CPF em `accounts`**. Não permite manter a conta encerrada e abrir outra para o
  mesmo CPF.

### D3. API do Accounts

| Método e caminho | Sucesso | Erros (`code`) |
|---|---|---|
| `POST /accounts` (header `Idempotency-Key`: UUID; corpo `{fullName, cpf, birthDate}`) | `201 Created`, `Location: /accounts/{accountId}`, corpo `{accountId, status, cpf (mascarado), canMoveMoney, openedAt}` | `400 IDEMPOTENCY_KEY_MISSING`, `400 IDEMPOTENCY_KEY_INVALID` (não é UUID), `400 VALIDATION_ERROR` (lista os campos), `422 ACCOUNT_INVALID_CPF`, `422 ACCOUNT_HOLDER_UNDERAGE`, `422 IDENTITY_EMAIL_MISSING`, `409 ACCOUNT_ALREADY_OPEN`, `409 ACCOUNT_CPF_ALREADY_REGISTERED`, `409 ACCOUNT_IDENTITY_ALREADY_LINKED`, `422 IDEMPOTENCY_KEY_REUSED`, `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`, `401 AUTHENTICATION_REQUIRED`, `503 SERVICE_UNAVAILABLE` |
| `GET /accounts/{accountId}` | `200`, corpo `{accountId, status, cpf (mascarado), canMoveMoney, openedAt, activatedAt}` | `400 VALIDATION_ERROR` (`accountId` que não é UUID), `404 ACCOUNT_NOT_FOUND` (também para conta de outro `sub`), `401 AUTHENTICATION_REQUIRED`, `503 SERVICE_UNAVAILABLE` |

- Os erros saem em `ProblemDetail` (`spring.mvc.problemdetails.enabled=true` e um `@RestControllerAdvice` que estende
  `ResponseEntityExceptionHandler`), com a propriedade `code`. O `401` também sai em `ProblemDetail`, por meio de um
  `AuthenticationEntryPoint` próprio, sempre com o `code` `AUTHENTICATION_REQUIRED`: token ausente, expirado ou com
  audiência errada recebem a mesma resposta, sem que o corpo revele o motivo da recusa.
- Com o banco do Accounts indisponível (falha de conexão ou tempo esgotado no acesso ao banco), a resposta é
  `503 SERVICE_UNAVAILABLE` em `ProblemDetail`. Nada é gravado, nem a chave de idempotência, e o cliente repete o
  pedido com a mesma chave (spec, "Decisões de consistência").
- A API é documentada pelo springdoc em `/v3/api-docs`. Cada endpoint nasce documentado, com corpo, headers e códigos
  de erro, na mesma tarefa que o implementa. O springdoc entra com o `POST /accounts` (5.3), como diz a ADR-0006. O
  `/v3/api-docs/**` fica liberado sem token na porta da aplicação, pelo `SecurityFilterChain` da 3.1: só documenta a
  API e não expõe dados. A interface do Swagger UI não é liberada, porque nenhum requisito a pede.
- Ordem das verificações de negócio, antes de gravar:
  1. claim `email` presente;
  2. CPF válido;
  3. idade mínima;
  4. o `sub` já tem titular? Se tem e o CPF é outro, `ACCOUNT_IDENTITY_ALREADY_LINKED`. Se é o mesmo CPF e existe
     conta não encerrada, `ACCOUNT_ALREADY_OPEN`;
  5. o CPF já tem titular de outro `sub`? Se tem, `ACCOUNT_CPF_ALREADY_REGISTERED`.

  Em pedidos concorrentes, as verificações 4 e 5 podem passar nos dois. As constraints de D2 garantem a invariante:
  ninguém passa. Mas elas **não decidem o código de erro**. Na mesma linha, `cpf` e `owner_subject` podem colidir ao
  mesmo tempo, e o PostgreSQL reporta a constraint que checar primeiro. Por isso, na violação de qualquer uma das três
  constraints, a transação é desfeita e as verificações 4 e 5 são **refeitas numa nova transação**. Agora o dado do
  vencedor já está visível, e as verificações decidem o código:
  - mesma identidade e mesmo CPF → `ACCOUNT_ALREADY_OPEN`;
  - mesma identidade e outro CPF → `ACCOUNT_IDENTITY_ALREADY_LINKED`;
  - outra identidade e mesmo CPF → `ACCOUNT_CPF_ALREADY_REGISTERED`.

  Se nenhuma verificação se aplicar (o vencedor não chegou a gravar), a abertura é tentada de novo uma única vez.
  - Alternativa descartada: **mapear o código pelo nome da constraint violada**. O resultado depende da ordem de
    checagem dos índices, e o titular poderia receber `ACCOUNT_CPF_ALREADY_REGISTERED` sobre o próprio CPF.
- Alternativa descartada: **`403` para conta de outro titular**. Revelaria que o identificador existe.

### D4. Idempotência da abertura

Tabela `idempotency_records`: PK (`owner_subject`, `idempotency_key`), `request_hash` (SHA-256 do corpo canônico, com o
CPF normalizado; se o corpo não é parseável ou o CPF não normaliza, o hash é do corpo bruto, para que a repetição do
mesmo corpo inválido dê o mesmo `400`), `response_status`, `response_body` (JSONB, só com o CPF mascarado) e `created_at`.

Fluxo do `AccountOpeningService`:
1. Se já existe registro para (`sub`, chave): se o hash é igual, devolve a resposta gravada; se é diferente, devolve
   `IDEMPOTENCY_KEY_REUSED`.
2. Senão, abre uma única transação em que **o primeiro comando é o `INSERT` em `idempotency_records`** (com o
   `request_hash` e a resposta ainda vazia). Depois vêm, nesta ordem:
   - o `AccountHolder` (ou o existente do mesmo `sub` e CPF, no caso de reabertura);
   - a `Account`;
   - o `outbox_events` de `AccountOpened`;
   - o `UPDATE` do `idempotency_records` com a resposta `201`.

   A linha só fica visível no `COMMIT`, então ninguém de fora vê um registro "em processamento". O `INSERT` serve
   apenas para reservar a chave na PK antes de tocar nas outras constraints.
3. Se a PK de `idempotency_records` for violada (pedido concorrente com a mesma chave), a transação é desfeita e o passo
   1 é refeito. O segundo `INSERT` espera na PK até o primeiro terminar:
   - se o primeiro confirmou, o resultado gravado (`201` ou recusa) já está disponível, e os dois pedidos recebem a
     mesma resposta;
   - se o primeiro foi desfeito, o segundo segue normalmente;
   - se o primeiro não terminar dentro do `lock_timeout`, a resposta é `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`.

   Como a chave é reservada primeiro, dois pedidos idênticos nunca chegam às constraints de `account_holders` ao mesmo
   tempo. Isso evita que o segundo receba um `409` de CPF sobre uma chave que já tem `201`.
4. As recusas de negócio são gravadas em `idempotency_records` com a resposta da recusa, para que a repetição devolva a
   mesma recusa mesmo depois de a condição mudar (por exemplo, o titular completar 18 anos). São elas os `422` das
   verificações 1 a 3 de D3 (`IDENTITY_EMAIL_MISSING`, `ACCOUNT_INVALID_CPF` e `ACCOUNT_HOLDER_UNDERAGE`) e os `409` de
   unicidade (`ACCOUNT_ALREADY_OPEN`, `ACCOUNT_CPF_ALREADY_REGISTERED` ou `ACCOUNT_IDENTITY_ALREADY_LINKED`). Há dois
   caminhos:
   - **Recusa na verificação prévia** (verificações 1 a 5 de D3, antes de qualquer gravação): a transação de abertura
     não chega a começar. Uma transação curta grava direto o `idempotency_records` com o `422` ou o `409`.
   - **Violação de constraint durante a abertura**: a transação de abertura é desfeita, inclusive a reserva da chave.
     Uma nova transação refaz as verificações 4 e 5 de D3 para decidir o código e grava o `idempotency_records` com o
     `409`.

   Nos dois caminhos, se o `INSERT` da chave violar a PK, vale o passo 3.
5. O `400 VALIDATION_ERROR` também é gravado, desde que a identidade e a chave sejam válidas: uma data de nascimento
   futura, por exemplo, vira `422` quando a data passa, e a mesma chave deve devolver sempre a mesma resposta. Não são
   gravados: a chave ausente ou inválida (`IDEMPOTENCY_KEY_MISSING` e `IDEMPOTENCY_KEY_INVALID`), porque não há chave
   para reservar; o `401`, porque não há identidade; e o `503`, porque a transação não aconteceu e o cliente deve
   repetir com a mesma chave. Corrigir um pedido recusado muda o conteúdo e, portanto, exige uma nova chave
   (`IDEMPOTENCY_KEY_REUSED` na mesma chave).

- Alternativa descartada: **registro "em processamento" confirmado antes da operação, numa transação própria**. Exige
  uma máquina de estados e um tratamento de registros órfãos. Aqui, a reserva da chave e a operação estão na mesma
  transação, e por isso não existe registro órfão.
- Alternativa descartada: **gravar `idempotency_records` no fim da transação**. O segundo pedido idêntico trava antes
  na constraint de CPF, em vez de na PK da chave, e recebe uma recusa diferente do `201` do primeiro.
- Alternativa descartada: **checar e depois inserir, sem constraint (check-then-act)**. Dois pedidos passam na checagem
  ao mesmo tempo.
- Alternativa descartada: **chave global (sem o `sub`)**. Uma identidade poderia receber o resultado de outra.

### D5. Envelope e tópicos dos eventos

Envelope JSON (Jackson 3, `tools.jackson.databind.json.JsonMapper`), enviado como `String`:

```json
{ "eventId": "uuid", "eventType": "AccountOpened", "schemaVersion": 1,
  "occurredAt": "2026-10-07T21:00:00Z", "aggregateId": "uuid", "payload": { ... } }
```

| Evento | Tópico | Chave da mensagem | `aggregateId` | `payload` v1 |
|---|---|---|---|---|
| `AccountOpened` | `accounts.account-opened` | `accountId` | `accountId` | `{accountId, currency: "BRL"}` |
| `LedgerAccountCreated` | `ledger.ledger-account-created` | `accountId` | `ledgerAccountId` | `{ledgerAccountId, accountId, currency}` |

- A chave é o `accountId` nos dois tópicos, para manter a ordem por conta.
- O evento não leva dado pessoal (LGPD).
- Dead letter topics: `accounts.account-opened.dlt` e `ledger.ledger-account-created.dlt`.
- Os schemas ficam em `services/<serviço>/src/main/resources/events/<evento>.v1.json`, como documentação e base dos
  contratos.
- Alternativa descartada: **Schema Registry com Avro**. É tecnologia nova sem um problema descrito numa spec
  (Artigo X).
- Alternativa descartada: **`JacksonJsonSerializer` do Spring Kafka**. Acopla o tipo Java aos headers da mensagem. Com
  `String` e um mapper explícito, a versão do schema é tratada no próprio código.

### D6. Outbox

Tabela `outbox_events` em cada serviço: `id` (= `eventId`), `aggregate_id`, `event_type`, `schema_version`, `topic`,
`message_key`, `payload` (JSONB, o envelope completo), `traceparent`, `occurred_at` e `published_at` (nulo enquanto o
evento está pendente).

- `OutboxWriter` grava o evento na mesma transação do agregado (`@Transactional` obrigatório, propagation
  `MANDATORY`). No Ledger, ele é o adaptador (`adapter/out/outbox`) da porta `DomainEventPublisher`: recebe o evento de
  domínio e o converte no envelope, com tópico e chave (D5).
- `OutboxRelay` roda com `@Scheduled(fixedDelay)`. Ele busca até 100 pendentes com
  `ORDER BY occurred_at FOR UPDATE SKIP LOCKED`, envia cada um com `KafkaTemplate.send(...)` e espera o
  `CompletableFuture` com timeout. Em seguida, grava `published_at`.
- O producer usa `acks=all` e idempotência ativada. A entrega é pelo menos uma vez: os duplicados possíveis são
  tratados pelo consumidor (D7).
- Gauge `outbox.pending` (quantidade de eventos não publicados), nos requisitos "Visibilidade de eventos não
  publicados" (contas) e "Visibilidade de confirmações não publicadas" (contas-contabeis).
- Alternativa descartada: **Debezium (CDC)**. Exige Kafka Connect, mais uma tecnologia para operar (Artigo X).
- Alternativa descartada: **Spring Modulith (event publication registry e externalização)**. É uma dependência nova
  com outro modelo mental, e o ganho é pequeno para duas tabelas e um agendador.
- Alternativa descartada: **transações do Kafka sem Outbox**. Não tornam atômicos o `COMMIT` do PostgreSQL e o envio,
  e violariam o Artigo VI.

### D7. Consumidores idempotentes

Tabela `processed_events` (`event_id` PK, `processed_at`) em cada serviço. Numa única transação:
`INSERT ... ON CONFLICT DO NOTHING`. Se nenhuma linha foi inserida, o evento é ignorado. Se foi, aplica o efeito.

- **Ledger, `AccountOpenedListener`** (adaptador de entrada): traduz a mensagem e chama o `CreateLedgerAccountUseCase`.
  O `CreateLedgerAccountService` faz, na transação, o que segue: cria `LedgerAccount` com `INSERT ... ON CONFLICT (account_id) DO NOTHING` e,
  só se uma linha foi inserida, grava no Outbox o `LedgerAccountCreated`. Um segundo evento diferente para a mesma
  conta não insere nada e é tratado como duplicado, sem gerar uma segunda confirmação. No PostgreSQL, deixar a
  restrição UNIQUE ser violada abortaria a transação inteira, inclusive o `processed_events`. O `ON CONFLICT` evita
  isso.
- **Accounts, `LedgerAccountCreatedListener`**: chama `Account.activate(occurredAt)`. Numa conta já `ACTIVE`, nada
  muda. Para uma conta inexistente, lança `UnknownAccountException`, que não é reprocessável e vai para a DLT.
- O offset é confirmado depois do `COMMIT` (ack `RECORD`).
- `DefaultErrorHandler` com `ExponentialBackOff` (3 tentativas) e `DeadLetterPublishingRecoverer`. Erros de
  desserialização, de `schemaVersion` desconhecida e `UnknownAccountException` vão direto para a DLT.
- Alternativa descartada: **tópicos de retry não bloqueantes (`@RetryableTopic`)**. Quebram a ordem por conta, e o
  volume desta change não justifica. Podem ser reavaliados em Notifications.

### D8. Modelo de dados do Ledger

`ledger_accounts`: `id` (UUID v7, obtido pela porta `IdGenerator`, D1), `account_id` (UUID, **UNIQUE**), `currency` (CHAR(3), `BRL`) e
`created_at`. Não há coluna de saldo (Artigo III). As tabelas `ledger_entries` e `holds` nascem nas changes 002 e 004.
O `id` de `outbox_events` do Ledger também é UUID v7. Os dois vêm do gerador `UuidV7` do Ledger (D2), porque o insert
de `ledger_accounts` é nativo (`ON CONFLICT`) e não passa pelo gerador do Hibernate.

O Ledger não expõe API de negócio nesta change e **não é resource server**. Ele não tem Spring Security, e o único
HTTP que serve é o Actuator, na porta de management (D12). Quando a change 002 criar a primeira API do Ledger, ela
traz o `SecurityFilterChain` e a validação do token, como prevê a ADR-0005.

- Alternativa descartada: **resource server no Ledger já nesta change**. Seria configuração sem nenhuma requisição com
  token para proteger, testada só contra endpoints inexistentes.

### D9. Segurança

- O Keycloak (ADR-0005) roda no Compose com o realm `kipay` importado de `infra/keycloak/kipay-realm.json`. O realm
  tem:
  - o client público `kipay-cli`, para teste local com o fluxo de senha;
  - o client `accounts` como audiência;
  - o mapper do claim `email`;
  - três usuários de teste: `ana` e `bruno`, com e-mail, e `sem-email`, sem e-mail, para o cenário de identidade sem
    e-mail.
- **O e-mail não é obrigatório no perfil de usuário do realm.** A regra "abertura exige e-mail" é do Accounts
  (`IDENTITY_EMAIL_MISSING`), não do Keycloak. Com e-mail obrigatório no perfil, o Keycloak exigiria a atualização do
  perfil de `sem-email`, e o fluxo de senha devolveria `invalid_grant` ("Account is not fully set up"), tornando o
  cenário impossível de testar.
- O arquivo do realm é um **export versionado**. Ele é gerado com `kc.sh export --realm kipay --users realm_file`,
  executado com o servidor parado ou num container separado, apontando para o mesmo banco ou volume. Antes do commit,
  o arquivo passa por dois cuidados:
  - **Remoção dos key providers** (`components` do tipo `org.keycloak.keys.KeyProvider`, com `privateKey` RSA e os
    `secret` HMAC e AES). Na importação, o Keycloak gera chaves novas. Uma chave privada no Git permitiria a qualquer
    leitor forjar tokens válidos.
  - **Verificação de que não restou `privateKey` nem `secret` no arquivo.** Os clients são só públicos.

  Toda mudança no realm passa por um novo export, nunca por edição manual do JSON. As senhas dos usuários de teste são
  só para o ambiente local e ficam documentadas em `infra/README.md`, junto com o passo a passo do export.
- No Accounts, um bean `SecurityFilterChain` usa `authorizeHttpRequests` e a DSL com lambdas, com
  `oauth2ResourceServer(jwt)`, `issuer-uri` do realm e validação da audiência `accounts`. Só o `/v3/api-docs/**` e os endpoints
  `health` e `prometheus` do actuator (que a chain também alcança na porta de management) são liberados sem token (D3).
- A sessão é stateless, e o CSRF fica desligado só porque não há cookies.
- Autorização por recurso: o `AccountQueryService` busca por (`accountId`, `owner_subject` = `sub`).
- Os testes de negócio da API usam `SecurityMockMvcRequestPostProcessors.jwt()`, sem `@MockitoBean` do `JwtDecoder`.
- Os testes de `401` precisam passar pelo decoder e pelos validadores, que o `jwt()` pula. Eles geram um par de chaves
  RSA, assinam tokens com `NimbusJwtEncoder` (válido, expirado, de outra audiência, de outro issuer e assinado com
  outra chave) e usam um `JwtDecoder` de teste (`NimbusJwtDecoder.withPublicKey`) com o mesmo validador da produção,
  exposto por um método da configuração de segurança. Não há dependência nova: o `NimbusJwtEncoder` vem com o starter
  de resource server. A busca das chaves pelo `issuer-uri` fica coberta pelo smoke test (tarefa 9.1).
- Alternativa descartada: **container de Keycloak nos testes automatizados (módulo de terceiros do Testcontainers)**.
  Seria mais uma dependência e deixaria os testes mais lentos. O caminho real com o Keycloak é coberto pelo smoke test
  (tarefa 9.1).

### D10. Spring Cloud e testes de contrato dos eventos (vira ADR-0006)

**Pré-condição**: identificar o release train do Spring Cloud compatível com o Boot 4.1. A verificação vale para o
train como um todo. Os módulos previstos no roadmap devem ter versão compatível:
- Gateway, na variante `spring-cloud-starter-gateway-server-webmvc`, coerente com o stack servlet e as virtual
  threads;
- Config (server e client);
- Service Registry com Eureka (`spring-cloud-starter-netflix-eureka-server` e `-client`). A ADR pode registrar o
  adiamento do Registry se o Kubernetes da fase de plataforma o dispensar, mas a compatibilidade é verificada de
  qualquer forma;
- LoadBalancer;
- CircuitBreaker com Resilience4j.

A verificação usa a tabela de compatibilidade oficial e um POM de teste que importa o BOM e resolve esses módulos. O
risco real está nos testes de contrato de mensagem com Kafka e Jackson 3. Por isso, um contrato de mensagem de
brinquedo precisa ser gravado pelo consumidor e verificado pelo produtor no `mvn verify` do POM de teste, com o
produtor publicando de fato no Kafka. Um `@SpringBootTest` que só sobe o contexto provaria pouco.

O mesmo POM resolve o springdoc 3.x com o Boot 4.1. O springdoc fica fora dos BOMs do Boot e do Spring Cloud, e o
Artigo XII exige ADR para essa exceção, então a versão fixada é registrada na ADR-0006. A versão do Pact também fica
fora dos BOMs e é registrada na mesma ADR.

Se não houver train compatível com o Boot 4.1, isso contradiz as restrições técnicas da constituição. Nesse caso, eu
paro e aviso, em vez de escolher outra versão do Boot.

**Mudança em 2026-10-08**: em 2026-07-06, o Spring Cloud Contract deixou de ser mantido pela equipe do Spring. Ele
saiu de todos os release trains (não está no BOM 2025.1.3), e o repositório foi arquivado. A manutenção passou para o
Stubborn.sh (`sh.stubborn`).

Escolha: **Pact JVM** (`au.com.dius.pact`, módulos `consumer:junit5` e `provider:junit5`, pacts V4 de mensagem
assíncrona):
- o **consumidor** define o que espera do evento num teste JUnit, e o Pact grava o arquivo de pact em
  `contracts/pacts/`, na raiz do monorepo (`@PactDirectory`). O teste entrega a mensagem ao handler real do listener;
- o **produtor** verifica os pacts dessa pasta (`@PactFolder`). O método `@PactVerifyProvider` chama o publisher real,
  lê a mensagem publicada no Kafka (Testcontainers) e a devolve para comparação;
- os arquivos de pact são **versionados** no repositório. Assim, cada serviço builda sozinho
  (`mvn -f services/<nome>`), sem Pact Broker e sem dependência Maven entre os serviços;
- o CI falha se um teste de consumidor gerar um pact diferente do versionado (`git diff --exit-code contracts/pacts`).
  Isso impede que o produtor seja verificado contra um pact desatualizado.

**Requisito: nenhum serviço depende de artefato Maven do outro.** Os contratos vão nos dois sentidos. Se cada serviço
declarasse o artefato de teste do outro como dependência, o reactor do Maven acusaria ciclo (accounts → ledger-tests
→ ledger → accounts-tests → accounts). Se o artefato viesse do `~/.m2` sem estar declarado, o primeiro `mvn verify`
num CI limpo falharia, porque ele ainda não existiria. Os dois caminhos também quebram a ADR-0002 (cada serviço
buildável sozinho com `mvn -f services/<nome>`). A pasta versionada de pacts atende ao requisito.

- Alternativa descartada: **Spring Cloud Contract**. Não é mais mantido pelo Spring e saiu dos release trains.
- Alternativa descartada: **Stubborn Contract** (continuação do Spring Cloud Contract). Manteria o mesmo modelo, mas
  está na versão 0.x e depende de um único mantenedor.
- Alternativa descartada: **JSON Schema compartilhado, validado nos dois lados**. É leve, mas depende de uma
  biblioteca de validação fora dos BOMs e com suporte incerto ao Jackson 3.
- Alternativa descartada: **jar de stubs ou de pacts do outro serviço como dependência de teste**. Cria ciclo no
  reactor e impede buildar um serviço sozinho.

### D11. Código duplicado entre serviços

O envelope, o Outbox e o consumidor idempotente são escritos em cada serviço. O `ProblemDetail` só existe no Accounts
nesta change, e o Ledger o ganha junto com a primeira API. A ADR-0002 deixou em aberto onde
ficaria o código compartilhado. Esta change **não cria** uma biblioteca comum: com dois serviços, a duplicação é pequena
e os testes de contrato protegem a compatibilidade. A duplicação vale também para a estrutura: o Outbox do Ledger é
adaptador de porta (D1), e o do Accounts é um pacote por funcionalidade. A decisão de criar uma biblioteca, se vier, gera uma nova ADR.

### D12. Observabilidade

- `logging.structured.format.console=ecs`. O `traceId` e o `spanId` entram via MDC do Micrometer Tracing.
- Nenhum corpo de requisição é logado. `Cpf.toString()` sai mascarado.
- `spring-boot-starter-opentelemetry` com a exportação desligada até a feature 8.
- Amostragem fixa em `management.tracing.sampling.probability=1.0` nos dois serviços, para que todo trace do ambiente
  local e do smoke test fique completo. O padrão do Boot (`0.1`) descartaria 9 em cada 10 traces quando a exportação
  for ligada. Outro valor é aplicado pela variável de ambiente `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`, sem
  configuração extra; o valor de produção é decidido na feature 8.
- Propagação por Kafka com `spring.kafka.template.observation-enabled` e `spring.kafka.listener.observation-enabled`.
- Para atravessar o Outbox, o `OutboxWriter` grava o `traceparent` corrente. O `OutboxRelay` abre o envio como filho
  desse contexto, para que o trace da requisição continue no consumidor.
- Métricas RED pelo `http.server.requests` e pelas observações do Kafka, além do `outbox.pending` (D6) e do gauge
  `accounts.pending.stale`: quantidade de contas PENDENTE com `opened_at` anterior a
  `now - kipay.accounts.pending-stale-threshold` (padrão `10m`, ajustável por configuração). O `Clock` é injetado para
  os testes.

  Não há cancelamento automático, e as contas continuam PENDENTE. Outras métricas de pendência (total e idade da mais
  antiga) só entram quando algum requisito pedir.
- Health checks do Actuator com os grupos `liveness` (só `livenessState`) e `readiness` (`readinessState` e `db`).
  Não há health indicator de Kafka: o Spring Boot 4.1 não traz um (conferido nos JARs `spring-boot-actuator-autoconfigure`,
  `spring-boot-health` e `spring-boot-kafka` 4.1.1, sem nenhuma classe de health ligada ao Kafka), e a abertura de conta é AP em relação ao canal de
  eventos (spec de contas, "Decisões de consistência"). Uma dependência que a consistência declarada tolera fora do ar
  não entra na readiness, senão o Kafka parado tiraria o serviço do balanceamento e recusaria pedidos que deveriam ser
  aceitos. A indisponibilidade do Kafka aparece no gauge `outbox.pending`, que cresce enquanto os eventos não saem.
- **O Actuator fica numa porta de management separada** (`management.server.port`: 9081 no Accounts e 9082 no Ledger).
  Essa porta não é publicada para fora da rede do Compose. O `SecurityFilterChain` do Accounts alcança as duas
  portas. Na porta da aplicação, qualquer rota fora de `/v3/api-docs/**` exige token. Na porta de management, só
  `health` e `prometheus` são liberados, com `EndpointRequest.to("health", "prometheus")`, e o isolamento vem da rede,
  para que o Prometheus e as sondas de saúde não precisem de token. Os demais endpoints do Actuator continuam
  autenticados.
  - Alternativa descartada: **liberar `/actuator/**` na porta da aplicação**. Expõe as métricas na mesma porta da API
    pública e mistura as regras de acesso das duas.

### D13. Infraestrutura local e CI

- `infra/docker-compose.yml` com:
  - `accounts-db` e `ledger-db` (PostgreSQL 17, portas 5433 e 5434);
  - `kafka` (imagem `apache/kafka`, KRaft, um nó);
  - `keycloak` (porta 8080), acrescentado na tarefa do realm (2.4);
  - `accounts` (8081) e `ledger` (8082), com as portas de management (9081 e 9082) só na rede interna. A imagem de
    cada um vem de um `Dockerfile` multi-stage em `services/<nome>/` (contexto de build na raiz do monorepo, com
    `mvn -f services/<nome>/pom.xml`, pois cada serviço tem o Spring Boot como pai e não depende do POM agregador;
    runtime em JRE 25 Alpine, que traz `wget` para o healthcheck); o healthcheck usa `/actuator/health/readiness` na porta de management.
- Versões de imagem fixadas.
- Os segredos vêm de `.env`, que fica fora do Git. Um `.env.example` vai versionado.
- Os tópicos são criados pelas aplicações com beans `NewTopic` (3 partições).
- `.github/workflows/ci.yml` com `actions/setup-java` (Temurin 25) e `mvn -B verify` na raiz.

### D14. Termos novos no glossário

Antes do código, entram em `docs/dominio/glossario.md`:
- Abertura de conta → `AccountOpening`;
- Pode movimentar → `canMoveMoney`;
- Evento processado → `ProcessedEvent`;
- Evento do Outbox → `OutboxEvent`;
- Registro de idempotência → `IdempotencyRecord`;
- Momento da abertura / da ativação → `openedAt` / `activatedAt`;
- Moeda → `currency`;
- Identidade do titular → `ownerSubject`;
- Nome completo → `fullName`;
- Data de nascimento → `birthDate`;
- E-mail → `email`;
- Idade mínima → `minimumAge`;
- Conta pendente além do limite → `stale pending account` (`accounts.pending.stale`);
- Eventos não publicados → `outbox.pending`;
- Não autenticado → `AUTHENTICATION_REQUIRED`;
- Serviço temporariamente indisponível → `SERVICE_UNAVAILABLE`.

## Risks / Trade-offs

- **[Risco] Conta presa em PENDENTE.** Um evento pode ir para a DLT ou o Ledger pode ficar fora por muito tempo. →
  Mitigação: os gauges `accounts.pending.stale` (mais de 10 minutos) e `outbox.pending` e o monitoramento das DLTs. Não há cancelamento automático, por decisão de negócio, e o
  reprocessamento da DLT é manual nesta change.
- **[Risco] Mensagens de recusa por vínculo permitem descobrir se um CPF já tem dono.** → O mesmo tratamento do risco
  abaixo.
- **[Risco] Mensagens de CPF já cadastrado permitem descobrir se um CPF tem conta.** → Mitigação parcial: a abertura
  exige autenticação, e a mensagem não repete o CPF. Rate limiting fica para a borda (feature 3). O risco fica
  registrado.
- **[Risco] O hash do corpo em `idempotency_records` deriva do CPF.** → O hash fica escopado por `sub` e não é exposto.
  Aceito.
- **[Trade-off] O CPF, o nome, a data de nascimento e o e-mail ficam em texto claro no banco do Accounts.** Isso é
  necessário para a unicidade e para KYC. Criptografia em repouso fica para a fase de plataforma.
- **[Risco] O e-mail guardado diverge do e-mail atual no Keycloak, se o usuário trocá-lo.** → Aceito nesta change. A
  sincronização fica para quando as Notifications precisarem do e-mail.
- **[Risco] O Pact fica fora dos BOMs**, e a versão é fixada e atualizada à mão. → Registrada na ADR-0006. O Pact
  4.7.5 foi verificado com o Boot 4.1.1 e o Jackson 3 e não traz o Jackson 2.
- **[Trade-off] O relay por polling adiciona latência (até o `fixedDelay`) e consultas periódicas.** Aceito em troca da
  simplicidade em relação ao CDC.
- **[Trade-off] Duplicação do envelope e do Outbox nos dois serviços (D11).**

## Migration Plan

Não há dados nem serviços existentes. As migrations Flyway criam os schemas do zero. O rollback é parar os containers
e remover os volumes do Compose.

## Open Questions

As perguntas de negócio foram respondidas (ver a seção "Decisões de negócio" da proposta). Estas podem ser respondidas
depois sem mudar a abordagem:

- Por quanto tempo guardar `idempotency_records` e `processed_events`, e como limpar os registros antigos (job
  agendado numa change futura)?
- Na reabertura depois do encerramento, o que fazer se o nome ou a data de nascimento informados divergirem dos
  guardados no `AccountHolder`? Nesta change, a reabertura reusa os dados guardados, e o caso só existe quando o
  encerramento for implementado. A change do encerramento decide.
