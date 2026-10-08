# ADR-0006 — Spring Cloud com Boot 4.1, testes de contrato de eventos e springdoc

**Status**: Aceita
**Data**: 2026-10-08

## Contexto

A constituição fixa o Spring Boot 4.1 e um release train do Spring Cloud compatível com o Boot 4.x (2025.1 Oakwood ou
sucessor). O Artigo XII exige que as versões venham dos BOMs do Boot e do Spring Cloud, com exceções só por ADR. O
Artigo VII exige testes de contrato nas integrações entre serviços.

A change 001 cria os primeiros eventos entre serviços (`AccountOpened` e `LedgerAccountCreated`), nos dois sentidos,
e a primeira API documentada em OpenAPI. Antes do código, três pontos precisam de decisão:
- qual train do Spring Cloud usar, verificado para todos os módulos previstos no roadmap (D10 do design da change
  001);
- qual ferramenta faz os testes de contrato de mensagem no Kafka, sem dependência Maven entre os serviços
  (ADR-0002: cada serviço builda sozinho com `mvn -f services/<nome>`);
- qual versão do springdoc usar, já que ele fica fora dos BOMs.

O design previa o Spring Cloud Contract, principalmente por ele estar no BOM do Spring Cloud. Na verificação, isso
mudou: em 2026-07-06, a equipe do Spring deixou de manter o Spring Cloud Contract e transferiu a manutenção para o
Stubborn.sh (`sh.stubborn`). Ele saiu de todos os release trains (está no BOM 2025.1.2, mas não no 2025.1.3), e o
repositório no GitHub foi arquivado.

Fontes:
- tabela de compatibilidade oficial: <https://spring.io/projects/spring-cloud> ("2025.1.x (Oakwood): 4.0.x, 4.1.x
  (Starting with 2025.1.2)");
- anúncio da 2025.1.3: <https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/> ("compatible with
  the Spring Boot 4.1.x line as well");
- mapeamento do Spring Initializr (`start.spring.io/actuator/info`, consultado em 2026-10-08): Boot 4.1 → Spring Cloud
  2025.1.3, e `springdoc-openapi` 3.1.0 para Boot ">=4.0.0 and <4.2.0-M1";
- saída do Contract: <https://spring.io/blog/2026/07/06/spring-cloud-contract-transition-to-stubbornsh> e
  <https://github.com/spring-cloud/spring-cloud-release/issues/540>.

## Decisão

O projeto usa o **Spring Cloud 2025.1.3 (Oakwood)** com o **Spring Boot 4.1.x**, o **Pact JVM 4.7.5** nos testes de
contrato de eventos e o **springdoc 3.1.1**. O Pact e o springdoc ficam fora dos BOMs, com as versões fixadas
registradas aqui. A 3.1.1 é o patch mais recente da linha 3.1 do springdoc no Maven Central (consultado em
2026-10-08), a mesma linha que o Initializr associa ao Boot 4.1 (3.1.0).

Versões verificadas (POM de teste com `spring-boot-starter-parent` 4.1.1 e o BOM 2025.1.3, resolvido com um
repositório local vazio):

| Módulo | Versão |
|---|---|
| `spring-cloud-starter-gateway-server-webmvc` | 5.0.3 |
| `spring-cloud-config-server` e `spring-cloud-starter-config` | 5.0.5 |
| `spring-cloud-starter-netflix-eureka-server` e `-client` | 5.0.2 |
| `spring-cloud-starter-loadbalancer` | 5.0.3 |
| `spring-cloud-starter-circuitbreaker-resilience4j` | 5.0.3 |
| `springdoc-openapi-starter-webmvc-ui` (fora dos BOMs) | 3.1.1 |
| `au.com.dius.pact.consumer:junit5` e `au.com.dius.pact.provider:junit5` (fora dos BOMs) | 4.7.5 |

Os módulos resolvem com o Spring Framework 7.0.9 e o Jackson 3.1.5.

**Configuração dos testes de contrato**, verificada no POM de teste com dois módulos e contratos nos dois sentidos:
- O **consumidor** escreve um teste com `@ExtendWith(PactConsumerTestExt.class)` e
  `@PactTestFor(providerName = "<produtor>", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)`.
  O método `@Pact` usa o `MessagePactBuilder` com o envelope do evento (`eventId`, `eventType`, `schemaVersion`,
  `occurredAt`, `aggregateId` e `payload`) e o tópico nos metadados. O teste entrega a mensagem
  (`V4Interaction.AsynchronousMessage`) ao handler real do listener.
- O pact é gravado em `contracts/pacts/`, na raiz do monorepo, com `@PactDirectory("../../contracts/pacts")`. A pasta é
  versionada.
- O **produtor** verifica os pacts com `@Provider("<produtor>")`, `@PactFolder("../../contracts/pacts")`,
  `MessageTestTarget` e `PactVerificationInvocationContextProvider`, junto com o `@SpringBootTest`. O método
  `@PactVerifyProvider("<descrição>")` chama o publisher real, lê do Kafka a mensagem publicada e a devolve como
  `MessageAndMetadata`, com o tópico e o `contentType` nos metadados. Os beans entram por parâmetro do `@BeforeEach`
  (`@Autowired`), sem injeção em campo.
- A descrição da interação (`expectsToReceive` no consumidor) precisa ser idêntica ao valor de `@PactVerifyProvider`
  no produtor.
- O CI roda `git diff --exit-code contracts/pacts` depois do build. Assim, um pact regenerado e não commitado não passa
  despercebido.

Resultados no POM de teste:
- `mvn verify` passou no reactor com um repositório local vazio (`-Dmaven.repo.local=<pasta nova>`);
- cada módulo também passou sozinho com `mvn -f <módulo> verify`;
- trocar a moeda do payload do produtor de `BRL` para `USD` quebrou a verificação
  (`$.payload.currency Expected 'USD' (String) to be equal to 'BRL' (String)`).

**Service Registry**: o Eureka continua na feature 3, como prevê o roadmap. Nesta verificação, ele foi coberto pela
tabela oficial do train e pela resolução das dependências; o uso real fica para a feature 3. O
Kubernetes só chega na feature 11, e até lá o Transfers (feature 4) já precisa de descoberta de serviço e
balanceamento. A troca do Eureka pelo discovery do Kubernetes é reavaliada na fase de plataforma.

## Alternativas consideradas

Critérios do design (D10): quem define o contrato; suporte a mensagens no Kafka; integração com o Maven e o Boot 4.1;
infraestrutura extra; como o contrato chega ao outro lado sem acoplar os builds; gestão de versão; compatibilidade com
o Jackson 3; custo de manutenção para uma pessoa.

| Critério | Pact JVM (escolhido) | Spring Cloud Contract | Stubborn Contract | JSON Schema compartilhado |
|---|---|---|---|---|
| Quem define o contrato | Consumidor | Produtor | Produtor | Ninguém (schema comum) |
| Mensagens no Kafka | Mensagem assíncrona V4; o produtor publica no Kafka no teste | O jar 5.0.3 não tem adaptador Kafka (só `KafkaMetadata`); cada lado implementa `MessageVerifierSender` e `Receiver` | O guia de transição anuncia blocos para Kafka; não verificado | Só valida o formato |
| Maven e Boot 4.1 | Verificado com o Boot 4.1.1 | Última versão 5.0.3, sem correções futuras; não verificado aqui | O guia de transição anuncia o Boot 4.1; não verificado | Depende da biblioteca |
| Infraestrutura extra | Nenhuma (pacts em pasta, sem Pact Broker) | Nenhuma | Nenhuma | Nenhuma |
| Contrato sem acoplar builds | Pasta versionada `contracts/pacts/` | `stubs://` sobre a pasta de contratos | Igual ao Contract | Pasta compartilhada |
| Gestão de versão | Fixada fora dos BOMs (esta ADR) | Fora do train desde a 2025.1.3 | Fixada fora dos BOMs | Fixada fora dos BOMs |
| Jackson 3 | Não traz Jackson nenhum: um módulo só com o Pact resolve 124 artefatos, sem `com.fasterxml.jackson` | O `ContractVerifierObjectMapper` do jar 5.0.3 usa `tools.jackson` | Não verificado | Suporte incerto |
| Manutenção | Ativo: 4.7.5 em 2026-08-10, 12 autores de commit em 2026 (GitHub) | Sem manutenção pelo Spring | primeira versão (0.1.0) em 2026-08-19 e atual (0.1.2) em 2026-08-23; commits humanos de duas pessoas em 2026, entre elas o criador original | Baixa, mas cobre pouco |

- **Spring Cloud Contract**: não é mais mantido pelo Spring e saiu dos release trains. Fixar a última versão (5.0.3)
  deixaria o projeto sem correções.
- **Stubborn Contract** (continuação do Spring Cloud Contract, `sh.stubborn` 0.1.2, <https://stubborn.sh/transition-guide/>,
  <https://github.com/stubborn-sh/stubborn-contract>): manteria o mesmo modelo e a
  mesma configuração, mas é um projeto novo, na versão 0.x. A manutenção foi transferida para uma pessoa, o criador
  original (anúncio do Spring), e em 2026 o repositório teve commits humanos de duas pessoas.
- **JSON Schema compartilhado, validado nos dois lados**: é leve, mas depende de uma biblioteca de validação fora dos
  BOMs com suporte incerto ao Jackson 3, e não prova que o produtor publica de fato a mensagem.
- **Jar de stubs ou de pacts do outro serviço como dependência de teste**: cria ciclo no reactor do Maven e impede
  buildar um serviço sozinho (ADR-0002).
- **Pact Broker**: dispensável num monorepo, porque os dois lados leem a mesma pasta versionada.

## Consequências

- Positivas:
  - O train escolhido cobre todos os módulos do roadmap com o Boot 4.1, sem trocar a versão do Boot.
  - Os contratos de eventos são verificados contra o publisher real, publicando no Kafka, e uma quebra de payload
    falha o build do produtor.
  - Cada serviço continua buildando sozinho, sem Pact Broker e sem dependência Maven entre os serviços.
  - Os contratos são definidos pelo consumidor, que declara só os campos que usa.
- Negativas e riscos aceitos:
  - O Pact e o springdoc ficam fora dos BOMs, e as versões são atualizadas à mão. Qualquer atualização gera uma nova
    ADR, que substitui esta.
  - Os pacts versionados podem ficar desatualizados se o consumidor mudar o teste e não commitar o arquivo. O
    `git diff --exit-code contracts/pacts` no CI mitiga esse risco.
  - No reactor, um produtor buildado antes do consumidor é verificado contra o pact já versionado, e não contra o
    regenerado no mesmo build. A verificação no CI cobre esse intervalo.
  - O produtor falha quando a pasta não tem nenhum pact para ele. Por isso, o pact do consumidor é gerado e commitado
    na mesma tarefa que cria o teste do produtor (tarefa 8.1).
  - Alguns módulos trazem o Jackson 2 (`com.fasterxml.jackson`) como dependência transitiva:
    - o springdoc 3.1.1, pelo `swagger-core-jakarta` 2.2.55 (`jackson-dataformat-yaml`), que entra no Accounts já na
      tarefa 5.3;
    - o Eureka client (`eureka-client` 2.0.6 e `spring-boot-jackson2`) e o Eureka server (`jackson-dataformat-xml`);
    - o Kafka embutido dos testes (`kafka-server`, `kafka_2.13`, `kafka-metadata` e `kafka-test-common-runtime`).

    O código do projeto não importa o Jackson 2 (Artigo XII).
  - O próprio Jackson 3 depende do `com.fasterxml.jackson.core:jackson-annotations` 2.21 (que também chega pelo
    `spring-cloud-config-client`): as anotações, como
    `@JsonProperty` e `@JsonInclude`, continuam no pacote `com.fasterxml.jackson.annotation`.
  - O POM de teste usou o Kafka embutido do `spring-kafka-test`, porque o Docker não estava disponível na máquina da
    verificação. Nos serviços, os testes de produtor usam o Kafka do Testcontainers (Artigo VII).
