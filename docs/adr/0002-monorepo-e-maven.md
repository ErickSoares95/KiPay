# ADR-0002 — Monorepo com serviços Maven independentes e POM agregador

**Status**: Aceita
**Data**: 2026-10-07

## Contexto

O projeto começa com dois serviços (Accounts e Ledger, na change 001) e cresce para cerca de nove ao longo do
roadmap, além da infraestrutura (Gateway, Config Server, Docker Compose e, depois, Kubernetes). É mantido por uma
pessoa e serve como portfólio: quem avalia precisa enxergar, num só lugar, a documentação, as specs do OpenSpec e
o código de todos os serviços.

As specs e ADRs atravessam serviços (a abertura de conta envolve Accounts e Ledger; a transferência envolve
Transfers, Accounts, Ledger e Antifraude). A constituição exige que cada serviço seja dono dos seus dados e se
integre apenas por API ou eventos (Artigo I), que tecnologia nova só entre quando resolve um problema descrito
numa spec (Artigo X) e que as versões de dependências fiquem centralizadas nos BOMs do Spring Boot e do Spring Cloud
(Artigo XII).

É preciso decidir como organizar o repositório e como cada serviço é buildado, sem criar acoplamento de código
entre serviços que a arquitetura proíbe para os dados.

## Decisão

Todos os serviços ficam num **único repositório**, cada um em `services/<nome>`. Cada serviço é um **projeto Maven
independente** que herda diretamente de `spring-boot-starter-parent` e importa o BOM do Spring Cloud quando
precisar dele. Um `pom.xml` na raiz, com `packaging` `pom`, atua **apenas como agregador**: lista os serviços em
`<modules>` para buildar e testar tudo de uma vez, sem que nenhum serviço herde dele.

## Alternativas consideradas

- **Polyrepo (um repositório por serviço)**: deixa os serviços totalmente isolados, mas espalha specs, ADRs e
  docs por vários repositórios ou exige um repositório só de documentação. Mudanças que atravessam serviços viram
  vários PRs coordenados, e o CI e o versionamento se multiplicam. Para uma pessoa e um portfólio, o custo é alto e
  o benefício (equipes e ciclos de deploy independentes) não existe.
- **Gradle**: tem builds incrementais e cache mais eficientes, mas o ecossistema Spring (Initializr, documentação,
  exemplos) usa Maven por padrão, e o ganho de desempenho não resolve nenhum problema que tenhamos hoje (Artigo X).
  Pode ser reavaliado por uma nova ADR se o tempo de build virar um problema.
- **Parent POM compartilhado com herança**: os serviços herdariam de um POM da raiz, que por sua vez herdaria do
  `spring-boot-starter-parent`. Isso centraliza plugins e propriedades, mas cria acoplamento: uma mudança no parent
  afeta todos os serviços ao mesmo tempo, os serviços deixam de ser buildáveis isoladamente e fica fácil empurrar
  dependências comuns que não são realmente compartilhadas. Também aproxima o projeto de uma "biblioteca comum"
  entre serviços, que tende a acoplar os bounded contexts.

## Consequências

- Positivas:
  - Specs, ADRs, documentação e código ficam no mesmo lugar e evoluem juntos; uma change do OpenSpec que
    atravessa serviços vira um único conjunto de commits.
  - Cada serviço continua independente: tem o próprio `pom.xml`, pode ser buildado e testado sozinho
    (`mvn -f services/<nome>`) e pode ser extraído para outro repositório sem reescrever o build.
  - Versões continuam centralizadas nos BOMs do Spring Boot e do Spring Cloud, como pede o Artigo XII.
  - Um único `mvn verify` na raiz builda e testa todos os serviços, o que simplifica o CI.
- Negativas e riscos aceitos:
  - Configurações de build repetidas em cada `pom.xml` (versão do Java, plugins); aceitamos a duplicação em troca
    do isolamento.
  - A versão do `spring-boot-starter-parent` precisa ser atualizada em cada serviço; divergências entre serviços
    são possíveis e devem ser verificadas na revisão.
  - O CI builda tudo a cada mudança, mesmo quando só um serviço mudou; se isso ficar lento, o caminho é filtrar
    por caminho alterado no CI, não trocar a estrutura.
  - Código compartilhado entre serviços (por exemplo, o tipo `Money` ou o envelope de eventos) não tem lugar
    definido por esta ADR. Se surgir necessidade real, a decisão vai para uma nova ADR.
