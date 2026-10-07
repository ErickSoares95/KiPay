# ADR-0004 — Idioma do projeto: documentação em português, código em inglês

**Status**: Aceita
**Data**: 2026-10-07

## Contexto

O projeto é um portfólio voltado primeiro a vagas no Brasil, sem fechar a porta para vagas internacionais. O
domínio é brasileiro (Pix, CPF, LGPD, SPI, MED) e a documentação já existente (constituição, visão geral,
conceitos do domínio e ADRs) está em português.

Quem avalia um portfólio no Brasil lê a documentação com mais atenção em português, e o domínio regulatório local é
melhor descrito na língua dele. Já o código com nomes em português é visto como pouco profissional tanto no mercado
internacional quanto na maior parte das empresas brasileiras, que escrevem código em inglês. Um recrutador de fora
olha principalmente o README e o código.

A primeira linha de código nasce na change 001 (abertura de conta), então a convenção precisa existir antes dela.

## Decisão

- **Documentação em português (pt-BR)**: constituição, ADRs, visão geral, docs de domínio e artefatos do OpenSpec
  (proposal, specs, design e tasks).
- **Código em inglês**: pacotes, classes, métodos, variáveis, eventos, tópicos, tabelas, colunas, endpoints,
  valores de enum, nomes de testes e mensagens de log. O texto do `@DisplayName` dos testes fica em português.
- **Termos de negócio** em português na documentação têm o nome em inglês correspondente definido em
  `docs/dominio/glossario.md`. Identificadores de código citados nos documentos (entre crases) usam o nome em inglês.
- **Siglas brasileiras sem tradução** (Pix, CPF, SPI, DICT, MED, LGPD) mantêm o nome original também no código.
- **README** em português como principal, com uma versão resumida em inglês (`README.en.md`).
- **Commits** com o tipo do Conventional Commits em inglês e a descrição em português, como no histórico atual.

## Alternativas consideradas

- **Tudo em português, inclusive o código**: mantém um único idioma, mas o código com nomes em português prejudica a
  avaliação no mercado internacional e destoa do que a maioria das empresas brasileiras pratica.
- **Tudo em inglês, inclusive a documentação**: favorece vagas internacionais, mas enfraquece a comunicação com o
  público prioritário (Brasil), obriga a reescrever a documentação existente e descreve um domínio regulatório
  brasileiro numa língua que não é a dele.
- **Documentação bilíngue completa**: dobra o custo de manter cada ADR e spec, sem ganho proporcional para o foco
  atual. Fica restrita ao README.

## Consequências

- Positivas:
  - A documentação conversa com o público prioritário e preserva o vocabulário regulatório brasileiro.
  - O código segue o padrão de mercado e é legível por qualquer recrutador ou revisor.
  - O glossário funciona como a linguagem ubíqua nos dois idiomas, evitando traduções diferentes do mesmo termo.
- Negativas e riscos aceitos:
  - Há uma camada de tradução entre docs e código; um termo novo precisa entrar no glossário antes de virar código.
  - O glossário precisa ser revisado a cada change; termos divergentes entre spec e código devem ser apontados na
    revisão.
  - Um avaliador internacional só lê o README em inglês; a profundidade das ADRs e specs fica acessível apenas via
    tradução.
