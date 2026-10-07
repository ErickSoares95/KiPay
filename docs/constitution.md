# Constituição do Projeto — Carteira Digital

> Princípios inegociáveis. Toda spec, plano e tarefa deve respeitá-los.
> Mudar um princípio exige nova versão desta constituição e uma ADR explicando o motivo.
> Ao adotar o OpenSpec, este conteúdo passa a ser o contexto do projeto lido em toda mudança.

**Versão**: 1.1.0 · **Ratificada em**: 2026-10 · **Última alteração**: 2026-10

---

## Artigo I — Um banco por serviço

Cada serviço é dono exclusivo dos seus dados. Nenhum serviço lê ou escreve no banco de outro.
A integração entre serviços acontece **apenas** por API (síncrona) ou por eventos (assíncrona).

## Artigo II — Dinheiro nunca é ponto flutuante

Valores monetários são armazenados como **inteiros em centavos** (`BIGINT`) acompanhados da moeda (`BRL`).
Na aplicação, usa-se um tipo de valor próprio (`Money`), nunca `double` ou `float`.

## Artigo III — Livro-razão imutável e partidas dobradas

- Todo movimento de dinheiro gera **lançamentos** de débito e crédito que somam zero.
- O saldo é **derivado** dos lançamentos; não existe campo de saldo editável manualmente.
- Lançamentos nunca são alterados ou apagados. Correções são feitas por **lançamento de estorno**.
- O saldo de uma conta de pagamento pré-paga **nunca fica negativo**.
- Movimentações de dinheiro entre contas internas acontecem dentro do serviço Ledger, numa única transação ACID (ver ADR-0001).

## Artigo IV — Idempotência obrigatória

- Toda operação que movimenta dinheiro ou cria recursos aceita uma **chave de idempotência**. A mesma chave enviada de novo devolve o mesmo resultado, sem efeito duplicado.
- Todo consumidor de evento é idempotente: processar o mesmo evento duas vezes não muda o resultado.

## Artigo V — Consistência declarada por operação

Toda spec deve conter a seção **"Decisões de consistência"**, classificando cada operação como **CP** ou **AP**,
com o comportamento esperado em caso de falha. Uma feature sem essa decisão não avança para o plano.

## Artigo VI — Eventos publicados via Outbox

Nenhum evento é publicado fora da transação que alterou os dados. Eventos são gravados numa tabela de outbox
na mesma transação e publicados depois. Eventos carregam `eventId`, `occurredAt`, `aggregateId` e versão do schema.

## Artigo VII — Testes como parte da definição de pronto

- Testes unitários com nomes descritivos (`@DisplayName`) e verificação explícita das interações relevantes.
- Testes de integração com dependências reais via **Testcontainers** (PostgreSQL, RabbitMQ, Kafka).
- Integrações entre serviços cobertas por **testes de contrato**.
- Cada critério de aceite da spec corresponde a pelo menos um teste.

## Artigo VIII — Observabilidade desde o primeiro serviço

- `traceId` propagado em todas as chamadas síncronas e assíncronas.
- Logs estruturados em JSON, sem dados pessoais em texto claro (CPF, e-mail e nome mascarados).
- Métricas RED (taxa, erros, duração) e health checks em todo serviço.

## Artigo IX — Segurança em profundidade

- Tokens validados no Gateway **e** em cada serviço.
- Autorização por recurso: um titular só acessa as próprias contas.
- Segredos fora do código e do repositório; nunca exibidos em logs ou respostas.
- Dados pessoais tratados conforme a LGPD: coletar apenas o necessário e mascarar em logs.

## Artigo X — Simplicidade antes de escala

- Começar com o menor número de serviços que respeite os bounded contexts.
- Um novo serviço só é criado com justificativa registrada em ADR.
- Tecnologia nova só entra quando resolve um problema descrito numa spec.

## Artigo XI — Erros padronizados

Todas as APIs retornam erros no formato `ProblemDetail` (RFC 9457), com um código de erro de negócio estável.

## Artigo XII — Somente APIs atuais

O código usa apenas as APIs atuais do stack definido abaixo. O jeito antigo aparece só em documentação, como contexto.
É **proibido** no código do projeto:

| Proibido | Usar no lugar |
|---|---|
| `RestTemplate` | `RestClient` ou HTTP Service Clients (`@HttpExchange`) |
| OpenFeign (`@FeignClient`) | HTTP Service Clients |
| Netflix Zuul, Hystrix, Ribbon | Spring Cloud Gateway, Resilience4j, Spring Cloud LoadBalancer |
| Spring Cloud Sleuth | Micrometer Tracing + OpenTelemetry |
| Spring Retry (projeto separado) | `@Retryable` / `@ConcurrencyLimit` do Spring Framework 7 |
| `WebSecurityConfigurerAdapter`, `authorizeRequests`, DSL com `.and()` | Bean `SecurityFilterChain`, `authorizeHttpRequests`, DSL com lambdas |
| `bootstrap.yml` | `spring.config.import` |
| `@EnableEurekaClient` | Nenhuma anotação (só a dependência) |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` |
| `spring-boot-starter-web`, `-aop`, `-oauth2-*` | `spring-boot-starter-webmvc`, `-aspectj`, `-security-oauth2-*` |
| Propriedades `spring.cloud.gateway.*` sem `server.webflux`/`server.webmvc` | `spring.cloud.gateway.server.webflux.*` ou `...server.webmvc.*` |
| `com.fasterxml.jackson.*` (Jackson 2) | `tools.jackson.*` (Jackson 3) |
| `javax.*` | `jakarta.*` |
| `JobBuilderFactory`, `StepBuilderFactory`, `CommandLineJobRunner` | `JobBuilder`, `StepBuilder`, `CommandLineJobOperator` |
| `logstash-logback-encoder` + XML | `logging.structured.format.console` |
| `ListenableFuture` | `CompletableFuture` |

Versões de dependências ficam centralizadas nos BOMs do Spring Boot e do Spring Cloud. Exceções (por falta de suporte
de alguma biblioteca ao stack atual) só com ADR.

---

## Restrições técnicas de base

| Item | Escolha |
|---|---|
| Linguagem | Java 25 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Spring Security 7), virtual threads habilitadas |
| Spring Cloud | Release train compatível com o Boot 4.x (2025.1 Oakwood ou sucessor) |
| Banco | PostgreSQL, um por serviço |
| Identidade | Keycloak (OAuth2 / OpenID Connect, JWT) |
| Mensageria | RabbitMQ (comandos e filas de trabalho); Kafka a partir da fase de streaming |
| Contratos | OpenAPI (springdoc) para APIs; schemas versionados para eventos |
| Execução local | Docker Compose; Kubernetes a partir da fase de plataforma |

## Governança

- Decisões de arquitetura ficam em `docs/adr/`, numeradas e imutáveis (uma decisão revista gera uma nova ADR que substitui a anterior).
- Cada feature é implementada **uma tarefa por vez**, com testes passando e um commit por tarefa.
- Revisões de código verificam conformidade com esta constituição antes de a mudança ser concluída.
