# CLAUDE.md

Carteira digital de portfólio em microserviços (Java 25, Spring Boot 4.1). A documentação fica em `docs/` e as
mudanças seguem o fluxo do OpenSpec (`openspec/`).

## Constituição

A constituição é carregada abaixo e deve ser seguida antes de qualquer tarefa.

@docs/constitution.md

- Em caso de conflito entre um plano (proposal, design, tasks ou pedido no chat) e a constituição, **pare e avise**
  em vez de seguir o plano. Mudar um princípio exige nova versão da constituição e uma ADR.
- Use apenas as APIs atuais do stack. Nada da tabela de proibidos do Artigo XII entra no código, nem em testes.

## Fluxo de trabalho

- Implemente **uma tarefa por vez**. Só considere a tarefa concluída com os testes passando.
- Faça um commit por tarefa, seguindo a seção [Commits](#commits).
- Ao final de cada tarefa, resuma o que mudou e liste os arquivos criados ou alterados.
- Ao final de cada tarefa, antes do commit, rode o subagente `revisor` com a seção "Tarefa" de
  `docs/processo/checklist-revisao.md`, passando os arquivos alterados, e corrija o que falhar.
- Todo achado de revisão é registrado em `docs/processo/licoes-aprendidas.md`, com a regra criada e onde ela foi
  aplicada.

## Código e testes

- Injeção de dependência sempre via construtor. Não use `@Autowired` em campo nem em setter.
- Testes com `@DisplayName` descritivo e verificação explícita do resultado e das interações relevantes.

## Idioma (ADR-0004)

- Documentação e artefatos do OpenSpec em português (pt-BR).
- Código em inglês: pacotes, classes, métodos, eventos, tópicos, tabelas, endpoints, enums e logs. O texto do
  `@DisplayName` fica em português.
- Use os nomes em inglês definidos em `docs/dominio/glossario.md`. Um termo de negócio novo entra no glossário antes
  de virar código; se ele faltar, pare e proponha a tradução.
- Pix, CPF, SPI, DICT, MED e LGPD não são traduzidos.

## ADRs

- ADRs ficam em `docs/adr/`, numeradas, seguindo `docs/adr/0000-template.md`.
- Nunca edite uma ADR com status "Aceita". Uma mudança de decisão gera uma nova ADR. Na ADR anterior, a única
  alteração permitida é o status passar a "Substituída por ADR-XXXX".

## Commits

- Mensagens no padrão Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `refactor:`).
- Um commit por tarefa.
- O commit termina somente com o trailer `Co-Authored-By: Claude <noreply@anthropic.com>`. Nunca incluir a linha
  "Generated with Claude Code" nem emojis na mensagem.
