# Tasks

> Uma tarefa por vez, com os testes passando e um commit por tarefa (Conventional Commits).
> Requisitos: `contas` = `specs/contas/spec.md`; `contas-contabeis` = `specs/contas-contabeis/spec.md`. Decisões: `Dn` =
> `design.md`. Decisões de negócio: seção "Decisões de negócio" da proposta.

## 1. Decisões e documentação

- [x] 1.1 Registrar as respostas às perguntas em aberto 1 a 6 da proposta e atualizar as specs com `/opsx:update`
  (requisitos "Dados cadastrais obrigatórios", "Idade mínima", "Validação do CPF", "CPF único", "Vínculo entre
  identidade e CPF", "Ativação após a confirmação do livro-razão" e "Visibilidade de contas pendentes"). Verificação:
  `openspec validate abertura-de-conta --strict` passa e nenhum requisito diz mais "depende da pergunta em aberto".
- [ ] 1.2 Acrescentar os termos de D14 ao `docs/dominio/glossario.md`. Verificação: todo identificador novo citado no
  design aparece no glossário.
- [ ] 1.3 Atualizar a seção 6 de `docs/arquitetura/visao-geral.md`: o Keycloak entra na feature 1, e o Gateway, o
  Registry e o Config Server continuam na 3. Verificação: o roadmap, a proposta e a ADR-0005 dizem a mesma coisa.
- [ ] 1.4 Escrever a ADR-0005, "Keycloak como provedor de identidade", seguindo `docs/adr/0000-template.md`. Ela
  registra o porquê da escolha (OIDC e JWT padrão, self-hosted no Docker Compose, sem custo nem conta em nuvem, realm
  versionável), a antecipação para a change 001 (Artigo IX) e as alternativas descartadas: Spring Authorization
  Server, AWS Cognito e Auth0/Okta. Verificação: todas as seções do template estão preenchidas e a ADR fica com o
  status "Aceita" depois da revisão no chat.
- [ ] 1.5 Verificar a compatibilidade do release train do Spring Cloud com o Boot 4.1 como um todo, e escrever a
  ADR-0006, "Spring Cloud com Boot 4.1 e testes de contrato de eventos" (D10). A ADR traz a comparação entre Spring
  Cloud Contract e Pact pelos critérios de D10. Verificação:
  - a tabela de compatibilidade oficial é citada na ADR;
  - um POM de teste no scratchpad, fora do repositório, com `spring-boot-starter-parent` 4.1.x e o BOM do train
    escolhido resolve (`mvn dependency:resolve`) os módulos Contract Verifier, Contract Stub Runner, Gateway, Config,
    LoadBalancer e CircuitBreaker com Resilience4j;
  - um teste `@SpringBootTest` mínimo com o Contract no classpath passa;
  - a ADR fica com o status "Aceita" depois da revisão.

  Se não houver train compatível, eu paro e aviso (conflito com a constituição). Se só o Contract falhar, as tarefas
  do grupo 8 são ajustadas com `/opsx:update`.

## 2. Fundação dos serviços e da infraestrutura

- [ ] 2.1 Criar o `pom.xml` raiz agregador e o `services/accounts` (D1), com Flyway, `JsonMapper` do Jackson 3 e
  virtual threads. Verificação: um teste `@SpringBootTest` com PostgreSQL e Kafka via Testcontainers
  (`@ServiceConnection`) sobe o contexto e `/actuator/health` responde `UP`.
- [ ] 2.2 Criar o `services/ledger` na mesma estrutura (D1). Verificação: o teste equivalente ao da 2.1 passa, e
  `mvn verify` na raiz builda os dois serviços.
- [ ] 2.3 Configurar a observabilidade de base nos dois serviços (D12): logs ECS, tracing com a exportação desligada e
  Prometheus. Verificação: um teste confirma que `/actuator/prometheus` expõe `http_server_requests` e que uma linha de
  log capturada (`OutputCaptureExtension`) é JSON e contém `traceId`.
- [ ] 2.4 Criar `infra/docker-compose.yml` e `.env.example` (D13), e subir o Keycloak com um realm `kipay` inicial
  (D9). Verificação: `docker compose config` é válido e `docker compose up` deixa os serviços healthy.
- [ ] 2.5 Configurar o realm `kipay` (clients `kipay-cli` e `accounts`, e-mail obrigatório, mapper de `email`,
  usuários `ana`, `bruno` e `sem-email`), exportar com `kc.sh export --realm kipay --users realm_file`, versionar o
  export em `infra/keycloak/kipay-realm.json` e documentar o processo de export e as credenciais locais em
  `infra/README.md` (D9). Verificação:
  - o JSON exportado não contém nenhum `secret` de client;
  - depois de `docker compose down -v && docker compose up`, o realm é reimportado do arquivo;
  - o token de `ana` obtido com `kipay-cli` traz os claims `email` e `aud` contendo `accounts`;
  - o token de `sem-email` não traz `email`.
- [ ] 2.6 Criar `.github/workflows/ci.yml` (D13). Verificação: o workflow roda verde no GitHub depois do push.

## 3. Segurança (contas: "Abertura de conta por pessoa física autenticada"; Artigo IX)

- [ ] 3.1 No Accounts, criar o `SecurityFilterChain` de resource server com validação de issuer e audiência, e um
  `AuthenticationEntryPoint` que responde em `ProblemDetail` (D9, D3). Verificação: testes MockMvc mostram que uma
  requisição sem token, com token expirado ou com audiência errada recebe `401` em `ProblemDetail`, e que
  `/actuator/health` responde sem token.
- [ ] 3.2 No Ledger, criar o mesmo `SecurityFilterChain`, liberando só o health (D8, D9). Verificação: testes MockMvc
  equivalentes aos da 3.1.
- [ ] 3.3 No Accounts, criar o `@RestControllerAdvice` base com `ProblemDetail` e a propriedade `code`, incluindo
  `VALIDATION_ERROR` com a lista de campos (D3). Verificação: um teste de um endpoint de teste confirma o formato
  RFC 9457 e o `code`.

## 4. Domínio do Accounts (contas)

- [ ] 4.1 Implementar o value object `Cpf`: normalização, formato, dígitos verificadores, dígitos repetidos e
  `toString()` mascarado (D2). Requisito: "Validação do CPF". Verificação: testes unitários cobrem os quatro cenários
  do requisito e o mascaramento.
- [ ] 4.2 Implementar as entidades `AccountHolder` (`cpf`, `ownerSubject`, `fullName`, `birthDate`, `email`) e
  `Account` (`status` com `PENDING`, `ACTIVE` e `CLOSED`, `activate`, `canMoveMoney` e `version`), e a migration `V1`
  com as constraints `uk_account_holders_cpf`, `uk_account_holders_owner_subject` e o índice parcial
  `uk_accounts_open_per_holder` (D2). Requisitos: "Conta não ativa não movimenta dinheiro", "CPF único", "Vínculo entre
  identidade e CPF" e a parte idempotente de "Ativação após a confirmação do livro-razão". Verificação:
  - testes unitários de `canMoveMoney` (PENDING, ACTIVE e CLOSED) e de `activate` repetido;
  - testes de repositório com Testcontainers confirmam as três constraints, inclusive que uma segunda conta é aceita
    quando a primeira está `CLOSED`.
- [ ] 4.3 Implementar o `AccountHolderPolicy` (idade mínima de 18 anos com `Clock` e fuso `America/Sao_Paulo`) e as
  validações do pedido: `fullName` obrigatório e não em branco, `birthDate` obrigatória, válida e não futura (D2).
  Requisitos: "Dados cadastrais obrigatórios" e "Idade mínima". Verificação:
  - testes unitários com `Clock` fixo: véspera do 18º aniversário recusada, dia do aniversário aceito, aniversário em
    29 de fevereiro tratado como em 1º de março nos anos não bissextos, e data futura recusada;
  - testes do validador para cada campo ausente ou inválido e para vários campos com problema ao mesmo tempo.

## 5. Abertura, idempotência e Outbox no Accounts (contas)

- [ ] 5.1 Implementar `OutboxEvent`, `OutboxWriter` (propagation `MANDATORY`) e a migration de `outbox_events` com
  `traceparent` (D6). Requisito: "Evento de conta aberta". Verificação: um teste de integração confirma que o
  `OutboxWriter` fora de uma transação falha, e que um rollback da transação do agregado não deixa linha no Outbox.
- [ ] 5.2 Implementar o `OutboxRelay` (`FOR UPDATE SKIP LOCKED`, envio com `CompletableFuture`, `published_at`, gauge
  `outbox.pending`) e o `NewTopic` de `accounts.account-opened` (D5, D6, D12). Requisito: "Evento de conta aberta",
  cenário "Conta criada gera evento". Verificação: um teste com Testcontainers Kafka confirma que o evento chega ao
  tópico com a chave `accountId`, o envelope completo e o mesmo `traceId` da requisição. Outro teste confirma que, com o
  Kafka parado, o evento continua pendente e é publicado quando o Kafka volta.
- [ ] 5.3 Implementar o `POST /accounts` (`{fullName, cpf, birthDate}`, com `email` do claim) com o
  `AccountOpeningService` e a migration de `idempotency_records`, conforme D3 e D4. Requisitos: "Abertura de conta por
  pessoa física autenticada" e "Idempotência da abertura". Verificação: testes de integração (MockMvc com `jwt()` e
  Testcontainers) para:
  - abertura aceita (`201`, `Location`, status `PENDING` e e-mail do claim gravado);
  - token sem `email` (`422 IDENTITY_EMAIL_MISSING`);
  - repetição com a mesma chave;
  - repetição de recusa;
  - chave com conteúdo diferente;
  - chave ausente ou inválida;
  - mesma chave por identidades diferentes;
  - criação com o Kafka indisponível.

  Cada teste confirma também a quantidade de contas e de linhas no Outbox.
- [ ] 5.4 Tratar a unicidade e o vínculo identidade ↔ CPF, inclusive em concorrência, com o mapeamento de constraints
  de D3 e os passos 3 e 4 de D4. Requisitos: "CPF único", "Vínculo entre identidade e CPF" e "Idempotência da
  abertura", cenário de repetições simultâneas. Verificação: testes de integração para:
  - segunda abertura pela mesma identidade (`ACCOUNT_ALREADY_OPEN`);
  - CPF com e sem pontuação;
  - reabertura depois de uma conta `CLOSED` inserida no banco (nova conta `PENDING`, conta encerrada intacta);
  - mesma identidade com outro CPF (`ACCOUNT_IDENTITY_ALREADY_LINKED`);
  - outra identidade com o mesmo CPF, ativo ou encerrado (`ACCOUNT_CPF_ALREADY_REGISTERED`);
  - pedidos concorrentes (dois threads com `CountDownLatch`): mesmo CPF com chaves diferentes, mesma chave, e mesma
    identidade com CPFs diferentes.

  Cada teste confirma a quantidade final de titulares, contas e linhas no Outbox.
- [ ] 5.5 Implementar o `GET /accounts/{accountId}` com autorização por `owner_subject` e CPF mascarado (D3, D9).
  Requisitos: "Consulta da própria conta" e "Conta não ativa não movimenta dinheiro" (`canMoveMoney` na resposta).
  Verificação: testes de integração para a própria conta, conta de outro `sub` (`404 ACCOUNT_NOT_FOUND`) e conta
  inexistente.
- [ ] 5.6 Garantir a proteção de dados pessoais nos logs e erros (D12). Requisito: "Proteção de dados pessoais".
  Verificação: testes com `OutputCaptureExtension` executam abertura aceita, CPF inválido, menor de idade e CPF já
  cadastrado. Eles confirmam que o CPF (com e sem pontuação), o nome, a data de nascimento e o e-mail não aparecem nos
  logs nem no corpo do erro. Um teste confirma que o payload de `AccountOpened` não tem dados pessoais.
- [ ] 5.7 Documentar a API no OpenAPI (springdoc), com os códigos de erro de D3. Verificação: um teste confirma que
  `/v3/api-docs` lista `POST /accounts`, o corpo `{fullName, cpf, birthDate}`, o header `Idempotency-Key` e as
  respostas `201`, `400`, `401`, `409`, `422`.

## 6. Conta contábil no Ledger (contas-contabeis)

- [ ] 6.1 Implementar `LedgerAccount` e a migration de `ledger_accounts` com `account_id` UNIQUE e sem coluna de saldo
  (D8). Requisito: "Criação da conta contábil a partir da conta aberta". Verificação: um teste de repositório com
  Testcontainers confirma a unicidade, e um teste de schema confirma que não há coluna de saldo.
- [ ] 6.2 Implementar no Ledger o `OutboxWriter`, o `OutboxRelay` e o `NewTopic` de `ledger.ledger-account-created`
  (D5, D6). Requisito: "Confirmação da criação da conta contábil". Verificação: testes equivalentes aos de 5.1 e 5.2.
- [ ] 6.3 Implementar o `AccountOpenedListener` com `processed_events`, o `DefaultErrorHandler` e a DLT
  `accounts.account-opened.dlt` (D7). Requisitos: "Criação da conta contábil a partir da conta aberta", "Uma única conta
  contábil por conta", "Confirmação da criação da conta contábil" e "Eventos inválidos não bloqueiam o processamento".
  Verificação: testes com Testcontainers Kafka para:
  - evento recebido (uma `LedgerAccount` e um `LedgerAccountCreated` no Outbox);
  - evento duplicado;
  - dois eventos diferentes para a mesma conta;
  - processamento concorrente;
  - `schemaVersion` desconhecida e payload inválido (vão para a DLT e o evento seguinte é processado);
  - falha temporária do banco (nova tentativa).

## 7. Ativação no Accounts (contas)

- [ ] 7.1 Implementar o `LedgerAccountCreatedListener` com `processed_events` e a DLT
  `ledger.ledger-account-created.dlt` (D7). Requisito: "Ativação após a confirmação do livro-razão". Verificação: testes
  com Testcontainers Kafka para:
  - confirmação recebida (`ACTIVE` e `activated_at` preenchido);
  - confirmação duplicada (`activated_at` inalterado);
  - conta desconhecida (DLT, sem bloquear a seguinte);
  - conta sem confirmação (continua `PENDING`, sem nenhuma alteração automática).
- [ ] 7.2 Expor os gauges `accounts.pending`, `accounts.pending.stale` (limite `kipay.accounts.pending-stale-threshold`,
  padrão `10m`) e `accounts.pending.oldest.age.seconds`, e o contador `accounts.opening.requests` (D12). Requisito:
  "Visibilidade de contas pendentes". Verificação: um teste com `Clock` controlado confirma no `/actuator/prometheus`:
  - uma conta com 9 minutos não entra em `accounts.pending.stale`;
  - uma conta com 11 minutos entra;
  - depois da ativação, ela sai;
  - com o limite configurado em `5m`, a contagem muda sem alterar o código.

## 8. Testes de contrato (Artigo VII; D10, conforme a ADR-0006)

- [ ] 8.1 Criar o contrato de `AccountOpened`: testes de produtor gerados no Accounts e Stub Runner no Ledger, que
  dispara o `AccountOpenedListener`. Requisitos: "Evento de conta aberta" e "Criação da conta contábil a partir da conta
  aberta". Verificação: `mvn verify` passa nos dois serviços, e alterar um campo do payload no produtor quebra o teste
  de produtor.
- [ ] 8.2 Criar o contrato de `LedgerAccountCreated`, no sentido inverso. Requisitos: "Confirmação da criação da conta
  contábil" e "Ativação após a confirmação do livro-razão". Verificação: a mesma da 8.1.

## 9. Verificação ponta a ponta

- [ ] 9.1 Criar o smoke test `infra/smoke/open-account.sh`, que usa o realm versionado (2.5) e faz o seguinte:
  - obtém o token de `ana` no Keycloak;
  - faz o `POST /accounts` duas vezes com a mesma chave;
  - consulta o `GET` até `ACTIVE`, com timeout;
  - tenta a mesma abertura com `bruno` e o CPF de `ana`;
  - tenta ler a conta de `ana` com o token de `bruno`;
  - tenta abrir conta com `sem-email`.

  Verificação: com o `docker compose up`, o script termina com código 0, e o `traceId` da abertura aparece nos logs do
  Accounts e do Ledger.
- [ ] 9.2 Atualizar o `README.md` (status, como subir o ambiente, como rodar o smoke test) e montar a tabela de
  rastreabilidade cenário → teste nesta change. Verificação: todo cenário das duas specs tem pelo menos um teste
  listado, e `mvn verify` na raiz passa.

## Workflow follow-up

- Revisar a conformidade com a constituição antes de concluir (Governança).
- Arquivar a change com `/opsx:archive` depois da revisão.
