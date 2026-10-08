# Tasks

> Uma tarefa por vez, com os testes passando e um commit por tarefa (Conventional Commits).
> Requisitos: `contas` = `specs/contas/spec.md`; `contas-contabeis` = `specs/contas-contabeis/spec.md`. Decisões: `Dn` =
> `design.md`. Decisões de negócio: seção "Decisões de negócio" da proposta.

## 1. Decisões e documentação

- [x] 1.1 Registrar as respostas às perguntas em aberto 1 a 6 da proposta e atualizar as specs com `/opsx:update`
  (requisitos "Dados cadastrais obrigatórios", "Idade mínima", "Validação do CPF", "CPF único", "Vínculo entre
  identidade e CPF", "Ativação após a confirmação do livro-razão" e "Visibilidade de contas pendentes"). Verificação:
  `openspec validate abertura-de-conta --strict` passa e nenhum requisito diz mais "depende da pergunta em aberto".
- [x] 1.2 Acrescentar os termos de D14 ao `docs/dominio/glossario.md`. Verificação: todo termo de negócio novo das
  specs e do design está no glossário. Nomes técnicos de implementação, como `JdbcClient` e `OutboxRelay`, não entram.
- [x] 1.3 Escrever a ADR-0005, "Keycloak como provedor de identidade", seguindo `docs/adr/0000-template.md`, e
  atualizar no mesmo commit a seção 6 de `docs/arquitetura/visao-geral.md` (o Keycloak entra na feature 1, e o
  Gateway, o Registry e o Config Server continuam na 3). A ADR registra:
  - o porquê da escolha: OIDC e JWT padrão, self-hosted no Docker Compose, sem custo nem conta em nuvem, realm
    versionável;
  - a antecipação para a change 001;
  - por que só o Accounts é resource server nesta change: o Ledger não recebe requisições com token e passa a validar
    quando expuser API (change 002). O Artigo IX fica atendido parcialmente, e essa pendência é aceita na ADR;
  - a aceitação formal de que a validação no Gateway fica para a feature 3;
  - as alternativas descartadas: Spring Authorization Server, AWS Cognito e Auth0/Okta.

  Verificação: todas as seções do template estão preenchidas, o roadmap e a proposta dizem o mesmo que a ADR, e a ADR
  fica com o status "Aceita" depois da revisão no chat.
- [x] 1.4 Verificar a compatibilidade do release train do Spring Cloud com o Boot 4.1 como um todo, e escrever a
  ADR-0006, "Spring Cloud com Boot 4.1, testes de contrato de eventos e springdoc" (D10). A ADR traz a escolha da
  ferramenta de contrato (Pact, D10), com a comparação das alternativas e o motivo da saída do Spring Cloud Contract,
  e as versões fixadas fora dos BOMs, do Pact e do springdoc (Artigo XII).
  Verificação:
  - a tabela de compatibilidade oficial é citada na ADR;
  - um POM de teste no scratchpad, fora do repositório, com `spring-boot-starter-parent` 4.1.x e o BOM do train
    escolhido resolve (`mvn dependency:resolve`) estes módulos: `spring-cloud-starter-gateway-server-webmvc`, Config,
    Eureka server e client, LoadBalancer, CircuitBreaker com Resilience4j, `springdoc-openapi-starter-webmvc-ui` 3.x
    e os módulos `consumer:junit5` e `provider:junit5` do Pact;
  - nesse POM, um contrato de mensagem de brinquedo (Kafka, payload JSON com Jackson 3) é gravado pelo consumidor e
    verificado pelo produtor, que publica de fato no Kafka, e `mvn verify` passa. Alterar o payload do produtor quebra
    a verificação;
  - o POM de teste tem dois módulos com contratos nos dois sentidos. Os pacts ficam numa pasta compartilhada, sem
    dependência Maven entre os módulos (D10). O `mvn verify` passa com um repositório local vazio
    (`-Dmaven.repo.local=<pasta nova>`), e cada módulo também builda sozinho com `mvn -f <módulo>`. A ADR registra a
    configuração usada (`@PactDirectory` e `@PactFolder`);
  - a ADR registra a decisão sobre o Service Registry (Eureka na feature 3, ou o adiamento se o Kubernetes o
    dispensar);
  - a ADR fica com o status "Aceita" depois da revisão.

  Se não houver train compatível, eu paro e aviso (conflito com a constituição).

## 2. Fundação dos serviços e da infraestrutura

- [x] 2.1 Criar o `pom.xml` raiz agregador, o `services/accounts` e o `services/ledger` (D1), com Flyway, `JsonMapper`
  do Jackson 3 e virtual threads. Verificação: em cada serviço, um teste `@SpringBootTest` com PostgreSQL e Kafka via
  Testcontainers (`@ServiceConnection`) sobe o contexto, e o health da porta de management responde `UP`. O
  `mvn verify` na raiz builda e testa os dois.
- [x] 2.2 Configurar a observabilidade de base nos dois serviços (D12): logs ECS, tracing com a exportação desligada,
  Prometheus e Actuator na porta de management separada (`management.server.port`). Verificação: um teste confirma
  que `/actuator/prometheus` responde na porta de management, expõe `http_server_requests` e não responde na porta da
  aplicação. Outro teste confirma que uma linha de log capturada (`OutputCaptureExtension`) é JSON e contém `traceId`.
- [x] 2.3 Criar `infra/docker-compose.yml` (sem Keycloak), com `accounts-db`, `ledger-db`, `kafka`, `accounts` e
  `ledger`, e o `.env.example` (D13). Criar um Dockerfile multi-stage por serviço (build com Maven e Temurin 25, a
  partir da raiz do monorepo com `mvn -f services/<nome>/pom.xml`, sem depender do POM agregador, e runtime com JRE 25
  Alpine), que o Compose usa para gerar as imagens
  de `accounts` e `ledger` (D13). Nos dois serviços, configurar também (D12):
  - o grupo `readiness` com `readinessState` e `db`, e o grupo `liveness` com só `livenessState`;
  - `management.tracing.sampling.probability=1.0` no `application.yml`.

  O healthcheck de `accounts` e `ledger` no Compose usa `/actuator/health/readiness` na porta de management.

  Ajustes de código que a 2.1 deixou para trás, na mesma tarefa:
  - tirar o `assertThat(flyway.info()).isNotNull()`, que nunca falha;
  - remover o `spring.flyway.locations` redundante;
  - corrigir o `@DisplayName` do teste de health, que hoje cita o Kafka.

  Verificação: `docker compose config` é válido, `docker compose up` deixa os serviços healthy, e as portas de
  management não são publicadas no host. Em cada serviço, testes confirmam que:
  - o `/actuator/health/readiness` responde `UP` com o banco no ar, e o Kafka não faz parte do grupo `readiness`;
  - o grupo `liveness` contém só `livenessState`;
  - o valor efetivo de `management.tracing.sampling.probability` no contexto é `1.0`.
- [x] 2.4 Acrescentar o Keycloak ao Compose e criar o realm `kipay` (D9): clients `kipay-cli` e `accounts`, mapper de
  `email`, e-mail **não** obrigatório no perfil, usuários `ana`, `bruno` e `sem-email`. Em seguida:
  - exportar com `kc.sh export --realm kipay --users realm_file --dir <pasta>`, com o servidor parado ou num container
    separado (no Keycloak 26.4.7, `--users realm_file` só vale com `--dir`; com `--file` o Keycloak exige `same_file`);
  - remover os key providers do arquivo;
  - versionar o arquivo em `infra/keycloak/kipay-realm.json`;
  - documentar em `infra/README.md` o passo a passo do export e as credenciais locais.

  Verificação:
  - uma busca no JSON exportado não encontra `privateKey` nem `secret`;
  - depois de `docker compose down -v && docker compose up`, o realm é reimportado do arquivo com chaves novas;
  - o token de `ana` obtido com `kipay-cli` traz os claims `email` e `aud` contendo `accounts`;
  - o token de `sem-email` é emitido sem `invalid_grant` e não traz `email`.
- [x] 2.5 Criar `.github/workflows/ci.yml` (D13). Verificação: o workflow roda verde no GitHub depois do push.

## 3. Segurança do Accounts (contas: "Abertura de conta por pessoa física autenticada"; Artigo IX)

- [ ] 3.1 No Accounts, criar o `@RestControllerAdvice` base com `ProblemDetail` e a propriedade `code`, incluindo
  `VALIDATION_ERROR` com a lista de campos e o banco indisponível como `503 SERVICE_UNAVAILABLE` (D3). Criar também o
  `SecurityFilterChain` de resource server com validação de issuer e audiência, e um `AuthenticationEntryPoint` que
  responde `401` no mesmo formato, com o `code` `AUTHENTICATION_REQUIRED` (D3, D9). Liberar o `/v3/api-docs/**` sem
  token, para o springdoc que entra na 5.3 (D3). Verificação:
  - um teste confirma o formato RFC 9457 e o `code` de um erro de validação;
  - um teste confirma que uma falha de acesso ao banco vira `503` em `ProblemDetail` com o `code` `SERVICE_UNAVAILABLE`;
  - testes MockMvc com tokens assinados no próprio teste (D9) mostram que uma requisição sem token, com token expirado,
    de outra audiência, de outro issuer ou com assinatura inválida recebe `401` em `ProblemDetail` com o `code`
    `AUTHENTICATION_REQUIRED`, e que um token válido passa;
  - uma rota qualquer sob `/v3/api-docs/**` não recebe `401` (o caminho está liberado), e outra rota sem token recebe;
  - o health e o Prometheus da porta de management continuam respondendo sem token.

  > **Plan mode recomendado** — foco: issuer, audiência, tokens assinados no teste e `401` em `ProblemDetail` sem bloquear a porta de management. Prompt sugerido: CLAUDE.md, seção "Modo plan".

## 4. Domínio do Accounts (contas)

- [ ] 4.1 Implementar o value object `Cpf`: normalização, formato, dígitos verificadores, dígitos repetidos e
  `toString()` mascarado (D2). Requisito: "Validação do CPF". Verificação: testes unitários cobrem os quatro cenários
  do requisito e o mascaramento.
- [ ] 4.2 Implementar as entidades `AccountHolder` (`cpf`, `ownerSubject`, `fullName`, `birthDate`, `email`) e
  `Account` (`status` com `PENDING`, `ACTIVE` e `CLOSED`, `activate`, `canMoveMoney` e `version`), com ids UUID v7, e a
  migration `V1` com as constraints `uk_account_holders_cpf`, `uk_account_holders_owner_subject` e o índice parcial
  `uk_accounts_open_per_holder` (D2). Requisitos: "Conta não ativa não movimenta dinheiro", "CPF único", "Vínculo entre
  identidade e CPF" e a parte idempotente de "Ativação após a confirmação do livro-razão". Verificação:
  - testes unitários de `canMoveMoney` (PENDING, ACTIVE e CLOSED, cenários do requisito "Conta não ativa não movimenta
    dinheiro") e de `activate` repetido;
  - um teste confirma que os ids gerados pelo `UuidV7` (D2), no `@IdGeneratorType` das entidades e no uso direto, são
    UUID versão 7;
  - testes de repositório com Testcontainers confirmam as três constraints, inclusive que uma segunda conta é aceita
    quando a primeira está `CLOSED`.
- [ ] 4.3 Implementar o `AccountHolderPolicy` (idade mínima de 18 anos com `Clock` e fuso `America/Sao_Paulo`) e as
  validações do pedido: `fullName` obrigatório e não em branco, `birthDate` obrigatória, válida e não futura (D2).
  Requisitos: "Dados cadastrais obrigatórios" e "Idade mínima". Verificação:
  - testes unitários com `Clock` fixo para a véspera do 18º aniversário (recusada), o dia do aniversário (aceito) e
    data futura (recusada);
  - para quem nasceu em 29 de fevereiro, num ano de 18º aniversário não bissexto: 28/02 recusado e 01/03 aceito;
  - testes do validador para cada campo ausente ou inválido e para vários campos com problema ao mesmo tempo;
  - um teste confirma que o nome completo é guardado sem os espaços nas pontas.

## 5. Abertura, idempotência e Outbox no Accounts (contas)

- [ ] 5.1 Implementar `OutboxEvent`, `OutboxWriter` (propagation `MANDATORY`) e a migration de `outbox_events` com
  `traceparent` (D6). Requisito: "Evento de conta aberta". Verificação: um teste de integração confirma que o
  `OutboxWriter` fora de uma transação falha, que um rollback da transação do agregado não deixa linha no Outbox, e que o
  id de `outbox_events` é UUID versão 7 (`UuidV7`, D2). O schema `events/AccountOpened.v1.json` passa a existir em
  `src/main/resources` (D5), e o payload gravado o respeita.
- [ ] 5.2 Implementar o `OutboxRelay` (`FOR UPDATE SKIP LOCKED`, envio com `CompletableFuture`, `published_at`, gauge
  `outbox.pending`) e o `NewTopic` de `accounts.account-opened`, e ligar `spring.kafka.template.observation-enabled`
  (D5, D6, D12). Requisitos: "Evento de conta aberta", cenário "Conta criada gera evento", e "Visibilidade de eventos
  não publicados". Verificação:
  - um teste com Testcontainers Kafka confirma que o evento chega ao tópico com a chave `accountId`, o envelope
    completo e, no header `traceparent`, o `traceId` da observação aberta pelo teste ao gravar o evento no Outbox (o
    `POST /accounts` só nasce na 5.3);
  - outro teste confirma que, com o Kafka parado, o evento continua pendente e `outbox.pending` cresce, e que o evento
    é publicado e `outbox.pending` volta a zero quando o Kafka volta.

  > **Plan mode recomendado** — foco: lock `FOR UPDATE SKIP LOCKED`, timeout do envio e propagação do `traceparent`. Prompt sugerido: CLAUDE.md, seção "Modo plan".

- [ ] 5.3 Implementar e documentar no OpenAPI o `POST /accounts` (`{fullName, cpf, birthDate}`, com `email` do claim).
  O `AccountOpeningService` e a migration de `idempotency_records` seguem D3 e D4, com o `INSERT` da chave como
  primeiro comando da transação. Acrescentar o springdoc (ADR-0006). Requisitos: "Abertura de conta por pessoa física
  autenticada" e "Idempotência da abertura". Verificação: um teste confirma que o `/v3/api-docs` responde `200` sem
  token, com um documento OpenAPI válido, no Boot 4.1 com o Jackson 3. Testes de integração (MockMvc com `jwt()` e
  Testcontainers) para:
  - abertura aceita (`201`, `Location`, corpo com `accountId`, `status` `PENDING`, `cpf` mascarado, `canMoveMoney`
    `false` e `openedAt`, e e-mail do claim gravado), com o `AccountOpened` gravado levando no `traceparent` o
    `traceId` da requisição;
  - pedido sem token (`401 AUTHENTICATION_REQUIRED`, nenhuma conta criada);
  - token sem `email` (`422 IDENTITY_EMAIL_MISSING`);
  - repetição com a mesma chave;
  - repetição de recusa (`409`, `422` e `400 VALIDATION_ERROR`), inclusive menor de idade repetido com a mesma chave depois de o `Clock`
    passar do 18º aniversário (mesma `422 ACCOUNT_HOLDER_UNDERAGE`, nenhuma conta criada);
  - banco do Accounts indisponível (`503 SERVICE_UNAVAILABLE`, sem registro de idempotência);
  - chave com conteúdo diferente;
  - chave ausente (`IDEMPOTENCY_KEY_MISSING`);
  - chave em formato inválido (`IDEMPOTENCY_KEY_INVALID`);
  - mesma chave por identidades diferentes;
  - criação com o Kafka indisponível;
  - um teste confirma que `/v3/api-docs` lista o `POST /accounts`, o corpo, o header `Idempotency-Key` e as respostas
    `201`, `400`, `401`, `409`, `422` e `503`.

  Cada teste de abertura confirma também a quantidade de contas e de linhas no Outbox.

  > **Plan mode recomendado** — foco: ordem dos comandos na transação e reserva da chave de idempotência. Prompt sugerido: CLAUDE.md, seção "Modo plan".

- [ ] 5.4 Tratar a unicidade e o vínculo identidade ↔ CPF, inclusive em concorrência: as constraints garantem a
  invariante, e as verificações 4 e 5 de D3, refeitas numa nova transação, decidem o código (D3, e D4 passos 3 e 4).
  Requisitos: "CPF único", "Vínculo entre identidade e CPF" e "Idempotência da abertura", cenário de repetições
  simultâneas. Verificação: testes de integração para:
  - segunda abertura pela mesma identidade (`ACCOUNT_ALREADY_OPEN`);
  - CPF com e sem pontuação;
  - reabertura depois de uma conta `CLOSED` inserida no banco (nova conta `PENDING`, conta encerrada intacta);
  - mesma identidade com outro CPF (`ACCOUNT_IDENTITY_ALREADY_LINKED`);
  - outra identidade com o mesmo CPF, ativo ou encerrado (`ACCOUNT_CPF_ALREADY_REGISTERED`).

  Testes concorrentes, com dois threads e `CountDownLatch`, repetidos 20 vezes para não passarem por sorte:
  - mesma identidade e mesmo CPF com chaves diferentes: sempre `201` e `ACCOUNT_ALREADY_OPEN`;
  - identidades diferentes e mesmo CPF: sempre `201` e `ACCOUNT_CPF_ALREADY_REGISTERED`;
  - mesma identidade com CPFs diferentes: sempre `201` e `ACCOUNT_IDENTITY_ALREADY_LINKED`;
  - mesma chave e mesmo conteúdo: os dois recebem `201` com o mesmo `accountId`, ou um deles recebe
    `IDEMPOTENCY_REQUEST_IN_PROGRESS`, nunca um `409` de CPF.

  Cada teste confirma a quantidade final de titulares, contas e linhas no Outbox.

  > **Plan mode recomendado** — foco: nova transação para decidir o código após violação de constraint, com testes concorrentes. Prompt sugerido: CLAUDE.md, seção "Modo plan".

- [ ] 5.5 Implementar e documentar no OpenAPI o `GET /accounts/{accountId}`, com autorização por `owner_subject` e
  CPF mascarado (D3, D9). Requisitos: "Consulta da própria conta" e "Conta não ativa não movimenta dinheiro"
  (`canMoveMoney` na resposta). Verificação:
  - testes de integração para a própria conta (com `openedAt` e `activatedAt`), conta de outro `sub`
    (`404 ACCOUNT_NOT_FOUND`), conta inexistente, `accountId` que não é UUID (`400 VALIDATION_ERROR`), pedido sem token (`401 AUTHENTICATION_REQUIRED`, idêntico para um id
    existente e para um inexistente, para não revelar se a conta existe) e banco do Accounts indisponível
    (`503 SERVICE_UNAVAILABLE`);
  - um teste confirma o endpoint e as respostas `401`, `404` e `503` no `/v3/api-docs`.
- [ ] 5.6 Garantir a proteção de dados pessoais nos logs e erros (D12). Requisito: "Proteção de dados pessoais".
  Verificação: testes com `OutputCaptureExtension` executam abertura aceita, CPF inválido, menor de idade e CPF já
  cadastrado. Eles confirmam que o CPF (com e sem pontuação), o nome, a data de nascimento e o e-mail não aparecem nos
  logs nem no corpo do erro. Um teste confirma que o payload de `AccountOpened` não tem dados pessoais.

## 6. Conta contábil no Ledger (contas-contabeis)

- [ ] 6.1 Implementar `LedgerAccount` (id UUID v7) e a migration de `ledger_accounts`, com `account_id` UNIQUE e sem
  coluna de saldo (D8). Requisito: "Criação da conta contábil a partir da conta aberta". Verificação: um teste de
  repositório com Testcontainers confirma a unicidade, um teste de schema confirma que não há coluna de saldo, e um
  teste confirma que o id de `ledger_accounts`, gravado por insert nativo, é UUID versão 7, gerado pelo `UuidV7` do
  Ledger (D2, D8).
- [ ] 6.2 Implementar no Ledger o `OutboxWriter`, o `OutboxRelay` e o `NewTopic` de `ledger.ledger-account-created`,
  e ligar `spring.kafka.template.observation-enabled` (D5, D6, D12). Requisitos: "Confirmação da criação da conta
  contábil" e "Visibilidade de confirmações não publicadas". Verificação:
  - um teste de integração confirma que o `OutboxWriter` fora de uma transação falha, e que um rollback da transação
    do agregado não deixa linha no Outbox;
  - um teste confirma que o id de `outbox_events` do Ledger é UUID versão 7, gerado pelo `UuidV7` do Ledger;
  - o schema `events/LedgerAccountCreated.v1.json` existe em `src/main/resources` (D5) e o payload gravado o respeita;
  - um teste com Testcontainers Kafka confirma que o `LedgerAccountCreated` chega ao tópico com a chave `accountId`, o
    envelope completo e, no header `traceparent`, o `traceId` da observação aberta pelo teste ao gravar o evento no
    Outbox (o listener que dá origem ao trace real só nasce na 6.3);
  - outro teste confirma que, com o Kafka parado, o evento continua pendente e `outbox.pending` cresce, e que o evento
    é publicado e `outbox.pending` volta a zero quando o Kafka volta.

  > **Plan mode recomendado** — foco: reaproveitar o desenho de 5.1 e 5.2 no Ledger, sem biblioteca compartilhada. Prompt sugerido: CLAUDE.md, seção "Modo plan".

- [ ] 6.3 Implementar o `AccountOpenedListener` com `processed_events`, `INSERT ... ON CONFLICT (account_id) DO NOTHING`
  em `ledger_accounts`, o `DefaultErrorHandler` e a DLT `accounts.account-opened.dlt` (D7), e ligar
  `spring.kafka.listener.observation-enabled` (D12). Requisitos: "Criação da
  conta contábil a partir da conta aberta", "Uma única conta contábil por conta", "Confirmação da criação da conta
  contábil" e "Eventos inválidos não bloqueiam o processamento". Verificação: testes com Testcontainers Kafka para:
  - evento recebido (uma `LedgerAccount` e um `LedgerAccountCreated` no Outbox);
  - evento duplicado;
  - dois eventos diferentes para a mesma conta (uma `LedgerAccount`, uma confirmação e o segundo evento registrado em
    `processed_events`);
  - processamento concorrente;
  - eventos publicados com o listener parado, processados todos quando ele sobe (cenário "Livro-razão indisponível por
    um período");
  - `schemaVersion` desconhecida e payload inválido (vão para a DLT e o evento seguinte é processado);
  - falha temporária do banco (nova tentativa);
  - falha persistente do banco (com o banco do Ledger indisponível em todas as tentativas, o evento vai para a DLT
    depois das 3 tentativas, sem conta contábil, e o evento seguinte é processado quando o banco volta);
  - trace contínuo: um `AccountOpened` publicado com `traceparent` é processado com o mesmo `traceId` (linha de log do
    listener), e o `LedgerAccountCreated` gravado no Outbox leva esse mesmo `traceId` no `traceparent`.

  > **Plan mode recomendado** — foco: `ON CONFLICT`, `processed_events`, DLT e os nove cenários de teste. Prompt sugerido: CLAUDE.md, seção "Modo plan".

## 7. Ativação no Accounts (contas)

- [ ] 7.1 Implementar o `LedgerAccountCreatedListener` com `processed_events` e a DLT
  `ledger.ledger-account-created.dlt` (D7), e ligar `spring.kafka.listener.observation-enabled` (D12). Requisito:
  "Ativação após a confirmação do livro-razão". Verificação: testes com Testcontainers Kafka para:
  - confirmação recebida (`ACTIVE` e `activated_at` preenchido);
  - confirmação duplicada (`activated_at` inalterado);
  - conta desconhecida (DLT, sem bloquear a seguinte);
  - conta sem confirmação (continua `PENDING`, sem nenhuma alteração automática);
  - trace contínuo: uma confirmação publicada com `traceparent` é processada com o mesmo `traceId` (linha de log do
    listener).

  > **Plan mode recomendado** — foco: ativação idempotente e conta desconhecida na DLT. Prompt sugerido: CLAUDE.md, seção "Modo plan".

- [ ] 7.2 Expor o gauge `accounts.pending.stale` (limite `kipay.accounts.pending-stale-threshold`, padrão `10m`)
  (D12). Requisito: "Visibilidade de contas pendentes". Verificação: um teste com
  `Clock` controlado confirma no `/actuator/prometheus` da porta de management:
  - uma conta com 9 minutos não entra em `accounts.pending.stale`;
  - uma conta com 11 minutos entra;
  - depois da ativação, ela sai;
  - com o limite configurado em `5m`, a contagem muda sem alterar o código.

## 8. Testes de contrato (Artigo VII; D10, conforme a ADR-0006)

- [ ] 8.1 Criar os pacts de `AccountOpened` (consumidor Ledger, produtor Accounts) e de `LedgerAccountCreated`
  (consumidor Accounts, produtor Ledger). Os testes de consumidor gravam os pacts em `contracts/pacts/`, versionado no
  repositório. Os testes de produtor dos dois serviços verificam esses pacts publicando de fato no Kafka
  (Testcontainers), sem dependência Maven entre os serviços (D10). O `ci.yml` ganha o passo
  `git diff --exit-code contracts/pacts`. Requisitos: "Evento de conta aberta", "Criação da conta contábil a partir da
  conta aberta", "Confirmação da criação da conta contábil" e "Ativação após a confirmação do livro-razão".
  Verificação:
  - `mvn verify` na raiz passa com um repositório local vazio;
  - `mvn -f services/accounts verify` e `mvn -f services/ledger verify` passam sozinhos;
  - alterar um campo do payload em qualquer produtor quebra o teste de produtor correspondente;
  - o CI falha quando um pact gerado difere do versionado.

  > **Plan mode recomendado** — foco: pacts nos dois sentidos, injeção por construtor no teste de produtor e passo do CI. Prompt sugerido: CLAUDE.md, seção "Modo plan".

## 9. Verificação ponta a ponta

- [ ] 9.1 Criar o smoke test `infra/smoke/open-account.sh`, que usa o realm versionado (2.4) e faz o seguinte:
  - obtém o token de `ana` no Keycloak;
  - faz o `POST /accounts` duas vezes com a mesma chave;
  - consulta o `GET` até `ACTIVE`, com timeout;
  - tenta a mesma abertura com `bruno` e o CPF de `ana`;
  - tenta ler a conta de `ana` com o token de `bruno`;
  - tenta abrir conta com `sem-email` (`IDENTITY_EMAIL_MISSING`).

  Verificação: com o `docker compose up`, o script termina com código 0, e o `traceId` da abertura aparece nos logs do
  Accounts e do Ledger. O token real do Keycloak aceito pelo Accounts cobre a busca de chaves pelo `issuer-uri`, que os
  testes de `401` (tokens assinados no teste) não exercitam (D9).
- [ ] 9.2 Atualizar o `README.md` (status, como subir o ambiente, como rodar o smoke test) e montar a tabela de
  rastreabilidade cenário → teste nesta change. Verificação: todo cenário das duas specs tem pelo menos um teste
  listado, e `mvn verify` na raiz passa.

## Workflow follow-up

- Revisar a conformidade com a constituição antes de concluir (Governança).
- Arquivar a change com `/opsx:archive` depois da revisão.
