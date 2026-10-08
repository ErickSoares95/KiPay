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

- Implemente **uma tarefa por vez**, cada uma numa **sessão nova**. O estado fica no repositório (tasks.md, ADRs,
  lições e git). Só considere a tarefa concluída com os testes passando.
- A conversa principal só orquestra. Ela não implementa nem lê o código da tarefa:
  1. chama o subagente `executor` com a change, o número da tarefa e as decisões já tomadas (ou o plano aprovado);
  2. chama o subagente `revisor` com a seção "Tarefa" de `docs/processo/checklist-revisao.md`, passando os arquivos
     que o executor listou e o resultado da "Verificação";
  3. se o revisor apontar falha, devolve a falha ao **mesmo** executor com `SendMessage` e roda o revisor de novo.
     Uma nova rodada do revisor só acontece quando houve falha; observações fora da checklist são tratadas sem nova
     rodada;
  4. marca o checkbox, localizando a linha pela busca do número da tarefa (não por número de linha guardado), e faz
     o commit e o push.
- Faça um commit por tarefa, seguindo a seção [Commits](#commits).
- Ao final de cada tarefa, resuma o que mudou e liste os arquivos criados ou alterados.
- Todo achado de revisão é registrado em `docs/processo/licoes-aprendidas.md` (arquivo local, fora do Git), com a
  regra criada e onde ela foi aplicada.

## Modo plan

- **Use** nas tarefas com decisão de implementação ainda aberta, marcadas no tasks.md com "Plan mode recomendado",
  ou quando a tarefa mexe em vários serviços. O plano mostra os arquivos, a abordagem e os testes, e o usuário aprova
  antes de o executor gastar tokens com implementação.
- **Não use** nas tarefas mecânicas, com tudo definido no design. Nelas, o plano só acrescenta uma rodada.
- Fluxo: sessão nova → plan mode → prompt abaixo → aprovação → `executor` com o plano aprovado → `revisor` → commit.
- Prompt sugerido (troque `<N>` e `<foco>` pelo que está marcado na tarefa):

  ```
  Planeje a tarefa <N> da change abertura-de-conta. Leia só o texto da tarefa no tasks.md, as decisões (Dn) e os
  requisitos que ela cita, e o código que ela toca. O plano deve trazer: arquivos a criar ou alterar; a abordagem
  para <foco>; os testes que cobrem cada cenário citado, com o texto do @DisplayName; os riscos e as decisões que
  dependem de mim. Não implemente nada; o executor implementa depois da aprovação.
  ```

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
