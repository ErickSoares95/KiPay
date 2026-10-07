# TASKS — Fase 0: Fundação do repositório

> SDD "manual", como na aula 1: este arquivo é lido pelo Claude Code, uma tarefa por vez.
> Prompt de execução: `Leia TASKS-fase-0.md e docs/constitution.md e implemente apenas a tarefa TN.`
> A partir da change 001 (abertura de conta), o fluxo passa a ser pelo OpenSpec.

**Nome do projeto**: `<projeto>` — substituir em todo o arquivo depois da T1.
**Pacote Java base**: `io.github.ericksoares95.<projeto>`

## Fora de escopo nesta fase

- Código de serviços, Docker Compose e CI. Eles entram na change 001, quando existir algo para rodar e testar
  (constituição, Artigo X: tecnologia só entra quando resolve um problema descrito numa spec).

## Estrutura alvo ao final da fase

```
<projeto>/
├── README.md
├── CLAUDE.md
├── .gitignore
├── docs/
│   ├── constitution.md
│   ├── arquitetura/visao-geral.md
│   ├── dominio/conceitos-carteira-digital.md
│   └── adr/
│       ├── 0000-template.md
│       ├── 0001-movimentacao-de-dinheiro-no-ledger.md
│       └── 0002-monorepo-e-maven.md
├── openspec/
│   ├── config.yaml
│   ├── specs/
│   └── changes/
├── services/   (vazio; accounts e ledger nascem na change 001)
└── infra/      (vazio; docker-compose nasce na change 001)
```

---

## T1 — Nome e repositório

**Responsável**: Você

### Objetivo
Definir o nome e criar o repositório público no GitHub.

### Passos
- Escolher o nome do projeto
- Criar o repositório público `<projeto>` no GitHub (sem README, sem .gitignore — eles vêm nas próximas tarefas)
- Clonar localmente e extrair na raiz o conteúdo da pasta `carteira-digital/` do `carteira-digital-base.zip`
  (já inclui este `TASKS-fase-0.md`)

### Validação
- `git remote -v` aponta para o repositório no GitHub
- Na raiz existem `TASKS-fase-0.md`, `constitution.md` e a pasta `docs/` (com `arquitetura/`, `adr/` e `dominio/`)

---

## T2 — Ambiente local

**Responsável**: Você

### Objetivo
Garantir as ferramentas necessárias instaladas.

### Passos
- JDK 25, Maven 3.9+, Docker Desktop, Node.js LTS (necessário para o OpenSpec) e Claude Code atualizado

### Validação
- `java -version` mostra a versão 25
- `mvn -v`, `docker compose version` e `node -v` executam sem erro

---

## T3 — Organizar a documentação base

**Responsável**: Claude Code

### Objetivo
Colocar a documentação existente na estrutura alvo.

### Passos
- Mover `constitution.md` da raiz para `docs/constitution.md`
- Garantir `docs/arquitetura/visao-geral.md`, `docs/dominio/conceitos-carteira-digital.md`, `docs/adr/0000-template.md`
  e `docs/adr/0001-...md` nos lugares da estrutura alvo
- Criar `services/` e `infra/` com um `.gitkeep` cada
- Criar `.gitignore` para Java, Maven, IntelliJ (`.idea/`, `*.iml`), logs e arquivos `.env`
- Não alterar o conteúdo dos documentos, apenas a localização

### Validação
- A árvore de pastas corresponde à estrutura alvo (exceto `openspec/`, `README.md`, `CLAUDE.md` e ADR-0002)
- Criar um arquivo `.env` de teste: `git status` não o lista. Apagar o arquivo depois

---

## T4 — ADR-0002: monorepo e Maven

**Responsável**: Você escreve o texto; revisão no chat com o Claude

### Objetivo
Registrar a decisão de organização do repositório e de build.

### Decisão proposta (confirmar ou ajustar)
- **Monorepo**: todos os serviços num único repositório, cada um em `services/<nome>`
- **Maven**: cada serviço é um projeto independente que herda de `spring-boot-starter-parent`; um `pom.xml` na
  raiz atua só como **agregador** (lista os módulos para buildar tudo junto, sem herança entre serviços)

### Passos
- Escrever `docs/adr/0002-monorepo-e-maven.md` seguindo o template, com contexto, decisão, alternativas
  (polyrepo; Gradle; parent POM compartilhado com herança) e consequências

### Validação
- O ADR tem todas as seções do template preenchidas e status "Aceita" após a revisão

---

## T5 — Instalar e inicializar o OpenSpec

**Responsável**: Você

### Passos
- `npm install -g @fission-ai/openspec@latest`
- Na raiz do repositório: `openspec init`, escolhendo o Claude Code como ferramenta

### Validação
- Existem `openspec/config.yaml`, `openspec/specs/` e `openspec/changes/`
- Os comandos do OpenSpec foram instalados para o Claude Code (pasta `.claude/`)
- `openspec list` executa sem erro (sem mudanças ativas)

---

## T6 — Contexto e regras do OpenSpec

**Responsável**: Claude Code

### Objetivo
Fazer o OpenSpec injetar a constituição e as lições de SDD em todo artefato gerado.

### Passos
Preencher `openspec/config.yaml`:

- `context`: resumo do projeto e da stack (Java 25, Spring Boot 4.1, PostgreSQL por serviço, Keycloak, RabbitMQ),
  os princípios da constituição em uma linha cada, e a indicação de que `docs/constitution.md`,
  `docs/arquitetura/visao-geral.md`, `docs/dominio/` e `docs/adr/` são a fonte da verdade
- `rules` por artefato:
  - **proposal**: explicar o problema e o valor; listar o que fica fora de escopo
  - **specs**: descrever o quê e o porquê, sem tecnologia; critérios de aceite verificáveis; casos de borda;
    seção obrigatória "Decisões de consistência" (CP/AP e comportamento na falha) por operação
  - **design**: validar contra a constituição antes de qualquer decisão; listar alternativas descartadas;
    indicar quando uma decisão deve virar ADR; usar apenas APIs atuais (Artigo XII)
  - **tasks**: cada tarefa aponta para o requisito que atende; validação objetiva com teste; testes junto com a
    tarefa, nunca só no final; usar os mesmos nomes do design

### Validação
- O YAML é válido e `openspec list` continua executando sem erro
- `rules` contém as quatro chaves: `proposal`, `specs`, `design`, `tasks`

---

## T7 — CLAUDE.md

**Responsável**: Claude Code

### Objetivo
Dar ao Claude Code as instruções permanentes do projeto.

### Conteúdo
- Ler `docs/constitution.md` antes de qualquer tarefa; em caso de conflito entre um plano e a constituição,
  parar e avisar em vez de seguir o plano
- Usar apenas APIs atuais (Artigo XII)
- Implementar uma tarefa por vez, com testes passando e um commit por tarefa (Conventional Commits)
- Testes com `@DisplayName` descritivo e verificação explícita; injeção de dependência via construtor
- Nunca editar ADRs com status "Aceita"; mudanças de decisão geram um novo ADR
- Ao final de cada tarefa, resumir o que mudou e quais arquivos foram criados ou alterados

### Validação
- Em uma sessão nova do Claude Code, perguntar "quais APIs são proibidas neste projeto?": a resposta deve
  listar os itens do Artigo XII

---

## T8 — README inicial

**Responsável**: Claude Code

### Conteúdo
- Nome e objetivo do projeto em duas frases; status "em construção"
- Diagrama Mermaid do mapa de comunicação (da visão geral)
- Stack, roadmap por fases e links para constituição, visão geral e ADRs
- Não descrever como pronto nada que ainda não existe

### Validação
- O README renderiza no GitHub com o diagrama visível

---

## T9 — Publicação

**Responsável**: Você

### Passos
- Revisar o histórico: um commit por tarefa, mensagens no padrão Conventional Commits (`docs: ...`, `chore: ...`)
- `git push`

### Validação
- No GitHub, o repositório mostra a estrutura alvo, o README renderizado e um commit por tarefa

---

## Depois da Fase 0

Primeira change pelo OpenSpec: `/opsx:explore` sobre a abertura de conta, revisada no chat, e então `/opsx:propose abertura-de-conta`.
