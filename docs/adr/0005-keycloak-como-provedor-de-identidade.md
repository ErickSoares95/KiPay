# ADR-0005 — Keycloak como provedor de identidade

**Status**: Aceita
**Data**: 2026-10-07

## Contexto

A constituição define o Keycloak (OAuth2 / OpenID Connect, JWT) como provedor de identidade do stack, e o Artigo IX
exige que os tokens sejam validados no Gateway **e** em cada serviço, com autorização por recurso. O roadmap
(`docs/arquitetura/visao-geral.md`, seção 6) previa o Keycloak só na feature 3 (borda e identidade), junto com o API
Gateway, o Service Registry e o Config Server.

A change 001 (abertura de conta) cria a primeira API do projeto, o `POST /accounts` e o `GET /accounts/{accountId}`
no Accounts. A conta precisa ficar vinculada à identidade de quem a abriu desde a abertura: cada identidade se liga a
um único CPF, e o titular só consulta as próprias contas. Sem um provedor de identidade, o vínculo e a autorização por
recurso só chegariam na feature 3.

Nesta change, o Ledger só consome eventos do Kafka. Ele não tem API de negócio, e o único HTTP que serve é o Actuator,
numa porta de management fora da rede pública. O API Gateway continua previsto para a feature 3.

## Decisão

O **Keycloak** é o provedor de identidade do projeto e entra já na change 001, antecipado da feature 3, rodando no
Docker Compose com o realm `kipay` importado de um export versionado. Nesta change, só o Accounts é resource server; o
Ledger passa a validar o token quando expuser a primeira API (change 002), e o Gateway, quando for criado (feature 3).

Motivos da escolha:
- **OIDC e JWT padrão**: o Accounts valida o token com o suporte nativo do Spring Security
  (`spring-boot-starter-security-oauth2-resource-server`), sem biblioteca do fornecedor.
- **Self-hosted no Docker Compose**: sobe junto com o resto do ambiente local, sem depender de serviço externo.
- **Sem custo nem conta em nuvem**: qualquer pessoa clona o repositório e roda o ambiente completo.
- **Realm versionável**: clients, mappers e usuários de teste ficam num export JSON no repositório, e o ambiente é
  reproduzível.

Por que só o Accounts é resource server: o Artigo IX protege as requisições que chegam com token. Nesta change, só o
Accounts recebe esse tipo de requisição, e ele valida o token (issuer e audiência) e autoriza por recurso. O Ledger
não recebe nenhuma requisição com token, então nenhuma requisição com token passa sem validação. Configurar um
resource server no Ledger agora seria configuração testada só contra endpoints que não existem. Quando a change 002
criar a primeira API do Ledger, o `SecurityFilterChain` e a validação do token entram junto com ela.

**Aceitação formal**: até essas entregas, o atendimento ao Artigo IX é **parcial**, e esta ADR aceita as duas
pendências:
- a validação no Gateway, que chega com o próprio Gateway, na feature 3;
- a validação no Ledger, que chega com a primeira API dele, na change 002.

## Alternativas consideradas

- **Spring Authorization Server**: exigiria construir e manter um serviço de autorização próprio, mais um serviço no
  projeto (Artigo X), sem console de administração nem gestão de usuários prontos.
- **AWS Cognito**: exige conta na AWS e não roda no Docker Compose, então o ambiente local dependeria de serviço
  externo. A AWS só entra na fase de plataforma (feature 11).
- **Auth0 / Okta**: SaaS com conta obrigatória e plano gratuito com limites. Não é self-hosted, e a configuração do
  tenant não fica versionada no repositório como um realm exportado.

## Consequências

- Positivas:
  - O vínculo entre a conta e a identidade e a autorização por recurso (Artigo IX) valem desde a primeira API.
  - O ambiente local completo, inclusive o fluxo real de token, sobe com o Docker Compose, sem custo.
  - A feature 3 passa a tratar do Gateway, do Service Registry e do Config Server, com o provedor de identidade já
    pronto.
- Negativas e riscos aceitos:
  - A validação no Gateway fica pendente até a feature 3. Até lá, o Accounts é a única barreira, e o ambiente é só
    local.
  - O Ledger não valida token até a change 002. O risco é baixo porque ele não tem API de negócio, e o Actuator fica
    numa porta fora da rede pública. A proteção do consumidor Kafka (ACL no broker) fica para uma fase futura.
  - O Keycloak é mais um container no ambiente local, que fica mais pesado para subir.
  - O export do realm traz as chaves privadas e os secrets do realm. Antes de cada commit, os key providers precisam
    ser removidos e o arquivo conferido, sem `privateKey` nem `secret`. Toda mudança no realm passa por um novo
    export, nunca por edição manual.
  - Os testes automatizados usam JWTs de teste, sem um Keycloak real. O caminho real com o Keycloak é coberto só pelo
    smoke test do Compose.
