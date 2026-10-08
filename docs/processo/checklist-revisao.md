# Checklist de revisão

> Perguntas de sim/não para revisar os artefatos de uma change antes do apply e cada tarefa antes do commit.
> A fonte de cada item aparece entre parênteses quando existe. Os achados de revisão são registrados em
> `docs/processo/licoes-aprendidas.md`.

## Artefatos (antes do apply)

- [ ] A spec está livre de tecnologia, framework e detalhe de implementação? (`openspec/config.yaml`, regras de specs)
- [ ] Todo critério de aceite é verificável de forma objetiva? (`openspec/config.yaml`, regras de specs)
- [ ] Os casos de borda estão cobertos: repetição da mesma requisição, dados inválidos, falha de dependência e
  concorrência? (`openspec/config.yaml`, regras de specs)
- [ ] Toda spec tem a seção "Decisões de consistência", com CP ou AP e o comportamento na falha de cada operação?
  (constituição, Artigo V)
- [ ] A proposta lista explicitamente o que fica fora de escopo? (`openspec/config.yaml`, regras de proposal)
- [ ] Todas as perguntas em aberto que mudam specs, abordagem ou tarefas foram resolvidas?
- [ ] Toda regra de negócio está numa spec, e nenhuma existe só numa tarefa ou no design?
- [ ] Spec, design e API dizem a mesma coisa? Cada código de erro citado na spec existe no design, e cada operação da
  spec tem endpoint ou evento correspondente?
- [ ] O comportamento em concorrência está descrito passo a passo no design: a ordem das gravações na transação e como
  o código de erro é decidido depois de uma violação de constraint? (constituição, Artigo IV)
- [ ] O escopo se limita ao que os requisitos pedem, sem métrica, endpoint ou campo que nenhum requisito peça?
  (constituição, Artigo X)
- [ ] Toda tarefa aponta para um requisito e tem teste, e todo cenário tem pelo menos um teste? (constituição,
  Artigo VII; `openspec/config.yaml`, regras de tasks)
- [ ] A ordem das tarefas respeita as dependências, sem nenhuma tarefa verificar algo que só nasce numa tarefa
  posterior?
- [ ] Os nomes são os do glossário, e todo termo de negócio novo das specs e do design está no glossário? (ADR-0004;
  CLAUDE.md, Idioma)
- [ ] O design foi validado contra a constituição, artigo por artigo, com os conflitos apontados? (CLAUDE.md,
  Constituição; `openspec/config.yaml`, regras de design)
- [ ] Toda decisão tomada fora do repositório (no chat) está refletida em todos os artefatos e registrada em ADR ou
  spec? (CLAUDE.md, ADRs)
- [ ] Para cada dependência nova, a compatibilidade com o stack foi verificada exercitando o uso real, e não só o
  classpath? E toda versão fora dos BOMs do Spring Boot e do Spring Cloud tem ADR? (constituição, Artigo XII)
- [ ] A infraestrutura de teste (Compose, realm do Keycloak) consegue reproduzir todos os cenários da spec?

## Tarefa (antes do commit)

- [ ] Os testes passam e a "Verificação" da tarefa foi cumprida? (CLAUDE.md, Fluxo de trabalho)
- [ ] Cada cenário que a tarefa cita tem teste, inclusive os de concorrência? (constituição, Artigo VII)
- [ ] O código está livre de tudo o que a lista do Artigo XII proíbe, inclusive nos testes? As anotações de
  `com.fasterxml.jackson.annotation` são permitidas; o resto de `com.fasterxml.jackson.*` não. (constituição, Artigo XII;
  ADR-0007)
- [ ] Os nomes de classes, métodos, eventos, tópicos, tabelas e endpoints são os do glossário? (ADR-0004; CLAUDE.md,
  Idioma)
- [ ] Se a tarefa altera o glossário, ele foi conferido contra o texto das specs e do design, e não só contra a lista
  de termos novos do design, incluindo os identificadores de agregado expostos em API ou evento? (ADR-0004; CLAUDE.md,
  Idioma)
- [ ] A injeção de dependência é sempre por construtor, sem `@Autowired` em campo ou setter? (CLAUDE.md, Código e
  testes)
- [ ] Todo teste tem `@DisplayName` descritivo em português? (CLAUDE.md, Código e testes; constituição, Artigo VII)
- [ ] Logs, erros e eventos estão livres de dados pessoais em texto claro (CPF, nome, e-mail, data de nascimento)?
  (constituição, Artigos VIII e IX)
- [ ] Os arquivos versionados estão livres de segredos: client secret, `privateKey`, senha real, `.env`?
  (constituição, Artigo IX)
- [ ] O commit segue o padrão do CLAUDE.md: Conventional Commits, um por tarefa, só com o trailer
  `Co-Authored-By: Claude <noreply@anthropic.com>`, sem emojis? (CLAUDE.md, Commits)
