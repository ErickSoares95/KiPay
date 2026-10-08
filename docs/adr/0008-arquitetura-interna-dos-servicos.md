# ADR-0008 — Arquitetura interna dos serviços

**Status**: Aceita
**Data**: 2026-10-08

## Contexto

A organização interna dos serviços foi decidida só de passagem na change `abertura-de-conta` (pacotes por
funcionalidade nos dois serviços), sem alternativas comparadas. Os dois serviços não têm a mesma importância:

- o **Accounts** é subdomínio de suporte, com regras simples (CPF, idade mínima, vínculo entre identidade e CPF);
- o **Ledger** é o domínio principal. Os Artigos III (partidas dobradas, imutabilidade, saldo nunca negativo) e IV
  (idempotência) vivem nele, e o Transfers, que virá depois, terá o mesmo perfil.

No Ledger, as regras precisam ser testáveis sem Spring, e nada pode deixar JPA ou Spring entrarem no modelo de
domínio sem que alguém perceba. A regra precisa ser verificada por teste, e não só por convenção.

## Decisão

- **Accounts**: pacotes por funcionalidade, com entidades ricas, como já está.
- **Ledger** (e o **Transfers**, quando for criado): Arquitetura Hexagonal.
  - `domain`: Java puro, sem Spring, JPA ou Jakarta Transactions.
  - `application`: portas de entrada (casos de uso) e de saída (persistência, publicação de eventos, geração de id),
    e os serviços que implementam os casos de uso. Só depende do JDK, do `domain`, de si mesma e de
    `org.springframework.transaction..`, para o `@Transactional`. Os serviços não usam `@Service`; são registrados
    como `@Bean` em `config/`.
  - `adapter`: entrada (Kafka) e saída (JPA/`JdbcClient`, Outbox, geração de UUID v7).
- **Serviços futuros**: Arquitetura Hexagonal quando o serviço for domínio principal ou concentrar regras de negócio
  críticas; pacotes por funcionalidade nos demais. A escolha é registrada no design da change que cria o serviço,
  citando esta ADR.
- Um teste **ArchUnit** no Ledger falha se `domain` depender de Spring ou JPA, e se `application` depender de algo fora
  da lista acima ou de `adapter`.
- O **ArchUnit** (`archunit-junit5`, escopo de teste) fica fora dos BOMs do Spring Boot e do Spring Cloud. A exceção
  do Artigo XII fica registrada aqui, com a versão **1.5.1**, a mais recente no Maven Central em 2026-10-08. A tarefa
  6.1 confirma que ela lê as classes do Java 25.

## Alternativas consideradas

- **Arquitetura Hexagonal em todos os serviços** — o Accounts tem regras simples. Portas e adaptadores só
  acrescentariam classes e mapeamentos, sem proteger um domínio complexo (Artigo X).
- **Camadas tradicionais (controller, service, repository)** — deixam o domínio acoplado ao JPA e ao Spring, e nada
  impede essa dependência de crescer. As regras do Ledger ficariam testáveis só com o contexto do Spring.
- **Domínio e aplicação sem nenhum Spring, com a transação num decorador no adaptador** — isolamento total, ao custo
  de uma classe a mais por caso de uso. O `@Transactional` na aplicação foi aceito como a única dependência permitida.
- **Convenção sem teste** — a regra se perde na primeira pressa. O ArchUnit a mantém verificada no `mvn verify`.

## Consequências

- Positivas:
  - O domínio do Ledger é testado sem Spring e sem banco, e o teste de arquitetura impede o acoplamento.
  - O Transfers herda um modelo já decidido.
  - A troca de adaptador (por exemplo, Outbox por outro mecanismo) não toca o domínio.
- Negativas e riscos aceitos:
  - Mais classes e mapeamento no Ledger. O Accounts e o Ledger passam a ter estruturas diferentes, e quem trabalha nos
    dois precisa conhecer as duas.
  - O `@Transactional` deixa a aplicação dependente de `org.springframework.transaction..`.
  - O ArchUnit é uma dependência fora dos BOMs, com versão atualizada à mão.
  - Entidades JPA do Ledger, quando houver leitura (change 002), serão classes de persistência separadas do domínio.
