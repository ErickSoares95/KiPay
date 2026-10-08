# Design

## Context

O repositório ainda não tem código. `services/` e `infra/` estão vazios. Esta change cria os dois primeiros serviços,
`accounts` e `ledger`, como projetos Maven independentes, e um `pom.xml` raiz que só agrega os módulos (ADR-0002). A
mensageria é o Kafka (ADR-0003), e o código usa os nomes em inglês do glossário (ADR-0004). A motivação está em
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
| VII. Testes | Unitários com `@DisplayName`, integração com Testcontainers (PostgreSQL e Kafka) e testes de contrato dos eventos | Não. A ferramenta de contrato pede ADR (ADR-0006, ver D10) |
| VIII. Observabilidade | Logs estruturados, `traceId` propagado por HTTP e Kafka (atravessando o Outbox), métricas RED, health checks | Não |
| IX. Segurança | O Accounts valida o JWT do Keycloak (issuer e audiência). A conta é vinculada ao `sub` do token, e o CPF sai mascarado. O Ledger não recebe requisições com token nesta change: só consome eventos, e o Actuator fica numa porta de management fora da rede pública (D12) | **Parcial e aceito na ADR-0005**: a validação no Gateway chega na feature 3, e a do Ledger quando ele expuser API (change 002). A proteção do consumidor Kafka (ACL no broker) fica para uma fase futura |
| X. Simplicidade | Só os dois serviços previstos. Tecnologia nova: Keycloak (já no stack, antecipado, ADR-0005) e a ferramenta de contrato (ADR-0006) | Não |
| XI. Erros padronizados | `ProblemDetail` com a propriedade `code` | Não |
| XII. APIs atuais | Starters `-webmvc` e `-security-oauth2-resource-server`, `SecurityFilterChain` com lambdas, Jackson 3 (`tools.jackson.*`), `jakarta.*`, `@MockitoBean`, `CompletableFuture`, `logging.structured.format.console` | Não |

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

Cada serviço herda de `spring-boot-starter-parent` 4.1.x, com Java 25 e `spring.threads.virtual.enabled=true`. Os
pacotes são organizados por funcionalidade: `account`, `outbox`, `idempotency`, `messaging`, `web` (erros e
segurança) no Accounts; `ledgeraccount`, `outbox` e `messaging` no Ledger.

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
  contrário do v4 aleatório. A `Idempotency-Key` do cliente aceita qualquer versão de UUID.
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
| `POST /accounts` (header `Idempotency-Key`: UUID; corpo `{fullName, cpf, birthDate}`) | `201 Created`, `Location: /accounts/{accountId}`, corpo `{accountId, status, cpf (mascarado), canMoveMoney, openedAt}` | `400 IDEMPOTENCY_KEY_MISSING`, `400 IDEMPOTENCY_KEY_INVALID` (não é UUID), `400 VALIDATION_ERROR` (lista os campos), `422 ACCOUNT_INVALID_CPF`, `422 ACCOUNT_HOLDER_UNDERAGE`, `422 IDENTITY_EMAIL_MISSING`, `409 ACCOUNT_ALREADY_OPEN`, `409 ACCOUNT_CPF_ALREADY_REGISTERED`, `409 ACCOUNT_IDENTITY_ALREADY_LINKED`, `422 IDEMPOTENCY_KEY_REUSED`, `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`, `401` |
| `GET /accounts/{accountId}` | `200`, corpo `{accountId, status, cpf (mascarado), canMoveMoney, openedAt, activatedAt}` | `404 ACCOUNT_NOT_FOUND` (também para conta de outro `sub`), `401` |

- Os erros saem em `ProblemDetail` (`spring.mvc.problemdetails.enabled=true` e um `@RestControllerAdvice` que estende
  `ResponseEntityExceptionHandler`), com a propriedade `code`. O `401` também sai em `ProblemDetail`, por meio de um
  `AuthenticationEntryPoint` próprio.
- A API é documentada pelo springdoc em `/v3/api-docs`. Cada endpoint nasce documentado, com corpo, headers e códigos
  de erro, na mesma tarefa que o implementa.
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
CPF normalizado), `response_status`, `response_body` (JSONB, só com o CPF mascarado) e `created_at`.

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
4. As recusas de unicidade de D3 (`ACCOUNT_ALREADY_OPEN`, `ACCOUNT_CPF_ALREADY_REGISTERED` ou
   `ACCOUNT_IDENTITY_ALREADY_LINKED`) são gravadas em `idempotency_records` com a resposta `409`, para que a repetição
   devolva a mesma recusa. Há dois caminhos:
   - **Recusa na verificação prévia** (verificações 4 e 5 de D3, antes de qualquer gravação): a transação de abertura
     não chega a começar. Uma transação curta grava direto o `idempotency_records` com o `409`.
   - **Violação de constraint durante a abertura**: a transação de abertura é desfeita, inclusive a reserva da chave.
     Uma nova transação refaz as verificações 4 e 5 de D3 para decidir o código e grava o `idempotency_records` com o
     `409`.

   Nos dois caminhos, se o `INSERT` da chave violar a PK, vale o passo 3.
5. Erros de validação (`400` e os `422` de CPF inválido, menor de idade e identidade sem e-mail) não são gravados: o
   pedido não foi processado, e o cliente pode corrigi-lo.

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
  `MANDATORY`).
- `OutboxRelay` roda com `@Scheduled(fixedDelay)`. Ele busca até 100 pendentes com
  `ORDER BY occurred_at FOR UPDATE SKIP LOCKED`, envia cada um com `KafkaTemplate.send(...)` e espera o
  `CompletableFuture` com timeout. Em seguida, grava `published_at`.
- O producer usa `acks=all` e idempotência ativada. A entrega é pelo menos uma vez: os duplicados possíveis são
  tratados pelo consumidor (D7).
- Gauge `outbox.pending` (quantidade de eventos não publicados).
- Alternativa descartada: **Debezium (CDC)**. Exige Kafka Connect, mais uma tecnologia para operar (Artigo X).
- Alternativa descartada: **Spring Modulith (event publication registry e externalização)**. É uma dependência nova
  com outro modelo mental, e o ganho é pequeno para duas tabelas e um agendador.
- Alternativa descartada: **transações do Kafka sem Outbox**. Não tornam atômicos o `COMMIT` do PostgreSQL e o envio,
  e violariam o Artigo VI.

### D7. Consumidores idempotentes

Tabela `processed_events` (`event_id` PK, `processed_at`) em cada serviço. Numa única transação:
`INSERT ... ON CONFLICT DO NOTHING`. Se nenhuma linha foi inserida, o evento é ignorado. Se foi, aplica o efeito.

- **Ledger, `AccountOpenedListener`**: cria `LedgerAccount` com `INSERT ... ON CONFLICT (account_id) DO NOTHING` e,
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

`ledger_accounts`: `id` (UUID v7, gerado na aplicação), `account_id` (UUID, **UNIQUE**), `currency` (CHAR(3), `BRL`) e
`created_at`. Não há coluna de saldo (Artigo III). As tabelas `ledger_entries` e `holds` nascem nas changes 002 e 004.
O `id` de `outbox_events` do Ledger também é UUID v7.

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
  `oauth2ResourceServer(jwt)`, `issuer-uri` do realm e validação da audiência `accounts`.
- A sessão é stateless, e o CSRF fica desligado só porque não há cookies.
- Autorização por recurso: o `AccountQueryService` busca por (`accountId`, `owner_subject` = `sub`).
- Os testes de API usam `SecurityMockMvcRequestPostProcessors.jwt()`, sem `@MockitoBean` do `JwtDecoder`.
- Alternativa descartada: **container de Keycloak nos testes automatizados (módulo de terceiros do Testcontainers)**.
  Seria mais uma dependência e deixaria os testes mais lentos. O caminho real com o Keycloak é coberto pelo smoke test
  (tarefa 9.1).

### D10. Spring Cloud e testes de contrato dos eventos (vira ADR-0006)

**Pré-condição**: identificar o release train do Spring Cloud compatível com o Boot 4.1. A verificação vale para o
train como um todo, não só para o Spring Cloud Contract. Os módulos previstos no roadmap devem ter versão compatível:
- Contract (Verifier e Stub Runner);
- Gateway, na variante `spring-cloud-starter-gateway-server-webmvc`, coerente com o stack servlet e as virtual
  threads;
- Config (server e client);
- Service Registry com Eureka (`spring-cloud-starter-netflix-eureka-server` e `-client`). A ADR pode registrar o
  adiamento do Registry se o Kubernetes da fase de plataforma o dispensar, mas a compatibilidade é verificada de
  qualquer forma;
- LoadBalancer;
- CircuitBreaker com Resilience4j.

A verificação usa a tabela de compatibilidade oficial e um POM de teste que importa o BOM e resolve esses módulos. O
risco real está no modo de mensageria do Contract com Kafka e Jackson 3. Por isso, um contrato de mensagem de
brinquedo precisa gerar o teste de produtor pelo plugin e passar no `mvn verify` do POM de teste. Um `@SpringBootTest`
que só sobe o contexto provaria pouco.

O mesmo POM resolve o springdoc 3.x com o Boot 4.1. O springdoc fica fora dos BOMs do Boot e do Spring Cloud, e o
Artigo XII exige ADR para essa exceção, então a versão fixada é registrada na ADR-0006.

Se não houver train compatível com o Boot 4.1, isso contradiz as restrições técnicas da constituição. Nesse caso, eu
paro e aviso, em vez de escolher outra versão do Boot.

Escolha proposta:

**Spring Cloud Contract**, no modo de mensageria:
- Os contratos de `AccountOpened` ficam no Accounts, que é o produtor, em
  `services/accounts/src/test/resources/contracts`. O Accounts gera os testes de produtor a partir deles.
- O Ledger usa o Stub Runner para disparar o evento no próprio listener.
- O mesmo vale, no sentido inverso, para `LedgerAccountCreated`, com os contratos no Ledger.

**Requisito: nenhum serviço depende de artefato Maven do outro.** Os contratos vão nos dois sentidos. Se cada serviço
declarasse o jar de stubs do outro como dependência de teste, o reactor do Maven acusaria ciclo (accounts →
ledger-stubs → ledger → accounts-stubs → accounts). Se os stubs viessem do `~/.m2` sem estar declarados, o primeiro
`mvn verify` num CI limpo falharia, porque eles ainda não existiriam. Os dois caminhos também quebram a ADR-0002
(cada serviço buildável sozinho com `mvn -f services/<nome>`).

Por isso, o consumidor gera os stubs na hora, a partir dos contratos do produtor que estão no monorepo. O candidato é o
Stub Runner apontando para a pasta de contratos do produtor (protocolo `stubs://file://...`, com geração de stubs a
partir dos contratos). A tarefa 1.4 confirma a configuração exata.

- Alternativa descartada: **jar de stubs do produtor como dependência de teste do consumidor**. Cria ciclo no reactor
  e impede buildar um serviço sozinho.

A ferramenta faz parte do release train do Spring Cloud, cujas versões vêm do BOM (Artigo XII). Mesmo assim, é
tecnologia nova no projeto, e por isso a escolha é registrada em uma ADR (ADR-0006) antes do uso.

A ADR-0006 compara o Spring Cloud Contract com o Pact pelos critérios abaixo:
- quem define o contrato (produtor ou consumidor);
- suporte a mensagens no Kafka;
- integração com o Maven e o Spring Boot 4.1;
- necessidade de infraestrutura extra (Pact Broker, que é dispensável num monorepo);
- como o contrato chega ao outro lado sem acoplar os builds dos serviços;
- gestão de versão (BOM do Spring Cloud × versão fixada fora do BOM);
- compatibilidade com o Jackson 3;
- custo de manutenção para uma pessoa.

- Alternativa considerada: **Pact (pact-jvm)**. É orientado ao consumidor, tem um ecossistema forte para HTTP e suporta
  mensagens. Num monorepo, ele funciona com os arquivos de pact no sistema de arquivos, sem Pact Broker, então a
  infraestrutura não é o argumento decisivo. O argumento mais forte a favor do Spring Cloud Contract é estar no BOM do
  Spring Cloud, com versão gerida junto com o resto do stack (Artigo XII). O Pact exigiria mais uma exceção de versão
  registrada em ADR.
- Alternativa descartada: **JSON Schema compartilhado, validado nos dois lados**. É leve, mas depende de uma
  biblioteca de validação fora dos BOMs e com suporte incerto ao Jackson 3.
- **Risco**: a compatibilidade do Spring Cloud Contract com o Boot 4.1 precisa ser confirmada na tarefa da ADR. Se o
  train existir mas o Contract falhar, a ADR registra o Pact ou a alternativa do JSON Schema.

### D11. Código duplicado entre serviços

O envelope, o Outbox e o consumidor idempotente são escritos em cada serviço. O `ProblemDetail` só existe no Accounts
nesta change, e o Ledger o ganha junto com a primeira API. A ADR-0002 deixou em aberto onde
ficaria o código compartilhado. Esta change **não cria** uma biblioteca comum: com dois serviços, a duplicação é pequena
e os testes de contrato protegem a compatibilidade. A decisão de criar uma biblioteca, se vier, gera uma nova ADR.

### D12. Observabilidade

- `logging.structured.format.console=ecs`. O `traceId` e o `spanId` entram via MDC do Micrometer Tracing.
- Nenhum corpo de requisição é logado. `Cpf.toString()` sai mascarado.
- `spring-boot-starter-opentelemetry` com a exportação desligada até a feature 8.
- Propagação por Kafka com `spring.kafka.template.observation-enabled` e `spring.kafka.listener.observation-enabled`.
- Para atravessar o Outbox, o `OutboxWriter` grava o `traceparent` corrente. O `OutboxRelay` abre o envio como filho
  desse contexto, para que o trace da requisição continue no consumidor.
- Métricas RED pelo `http.server.requests` e pelas observações do Kafka, além de:
  - `accounts.opening.requests` (tag `outcome`);
  - gauge `accounts.pending.stale`: quantidade de contas PENDENTE com `opened_at` anterior a
    `now - kipay.accounts.pending-stale-threshold` (padrão `10m`, ajustável por configuração). O `Clock` é injetado
    para os testes.

  Não há cancelamento automático, e as contas continuam PENDENTE. Outras métricas de pendência (total e idade da mais
  antiga) só entram quando algum requisito pedir.
- Health checks do Actuator (`db`, `kafka`) com os grupos `liveness` e `readiness`.
- **O Actuator fica numa porta de management separada** (`management.server.port`: 9081 no Accounts e 9082 no Ledger).
  Essa porta não é publicada para fora da rede do Compose. O `SecurityFilterChain` do Accounts protege a porta da
  aplicação. Na porta de management, os endpoints expostos (`health`, `prometheus`) são liberados com
  `EndpointRequest.toAnyEndpoint()`, e o isolamento vem da rede, para que o Prometheus e as sondas de saúde não
  precisem de token.
  - Alternativa descartada: **liberar `/actuator/**` na porta da aplicação**. Expõe as métricas na mesma porta da API
    pública e mistura as regras de acesso das duas.

### D13. Infraestrutura local e CI

- `infra/docker-compose.yml` com:
  - `accounts-db` e `ledger-db` (PostgreSQL 17, portas 5433 e 5434);
  - `kafka` (imagem `apache/kafka`, KRaft, um nó);
  - `keycloak` (porta 8080), acrescentado na tarefa do realm (2.4);
  - `accounts` (8081) e `ledger` (8082), com as portas de management (9081 e 9082) só na rede interna.
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
- Conta pendente além do limite → `stale pending account` (`accounts.pending.stale`).

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
- **[Risco] O Spring Cloud Contract pode não estar pronto para o Boot 4.1.** → Mitigação: verificar na tarefa da
  ADR-0006, com o Pact e o JSON Schema como alternativas já descritas.
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
