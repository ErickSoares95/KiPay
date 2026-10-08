# ADR-0007 — Anotações do Jackson 3 permitidas pelo Artigo XII

**Status**: Aceita
**Data**: 2026-10-08

## Contexto

O Artigo XII da constituição (versão 1.2.0) proíbe `com.fasterxml.jackson.*` (Jackson 2) no código e manda usar
`tools.jackson.*` (Jackson 3).

O Jackson 3 mudou o pacote do core e do databind para `tools.jackson.core` e `tools.jackson.databind`, mas manteve as
anotações no pacote antigo. O `tools.jackson.core:jackson-databind` 3.1.5, que vem do BOM do Boot 4.1, depende do
`com.fasterxml.jackson.core:jackson-annotations` 2.21. O MANIFEST do databind importa o pacote
`com.fasterxml.jackson.annotation`, onde ficam `@JsonProperty`, `@JsonInclude`, `@JsonFormat`, `@JsonTypeInfo` e as
demais anotações. O vínculo entre o databind 3 e esse artefato foi verificado na tarefa 1.4 (ADR-0006); a presença
dessas quatro anotações no jar `jackson-annotations-2.21.jar` foi conferida em 2026-10-08.

Lida ao pé da letra, a proibição alcança essas anotações. O artefato é da linha 2.x e é compartilhado pelo Jackson 2 e
pelo Jackson 3, mas é por ele que o Jackson 3 recebe as anotações; não é a API antiga que o artigo quer barrar. A regra
ficaria em conflito com o próprio stack, e cada ajuste de serialização teria de virar configuração global no
`JsonMapper` ou serializador escrito à mão.

## Decisão

O Artigo XII passa a proibir `com.fasterxml.jackson.*`, **exceto `com.fasterxml.jackson.annotation`**, usado pelo
Jackson 3. Continuam proibidos no código e nos testes o core, o databind e os módulos do Jackson 2
(`com.fasterxml.jackson.core`, `com.fasterxml.jackson.databind`, `com.fasterxml.jackson.dataformat`,
`com.fasterxml.jackson.datatype` e afins). A constituição passa para a versão 1.3.0.

## Alternativas consideradas

- **Manter a proibição ao pé da letra e não usar anotações do Jackson**: os `record`s do projeto quase não precisam
  delas, mas casos como formato de data, inclusão de nulos e polimorfismo exigiriam configuração global ou
  serializadores próprios, com mais código e sem ganho. A regra também ficaria em conflito com o stack que ela
  protege.
- **Liberar `com.fasterxml.jackson.*` inteiro**: abriria espaço para o `ObjectMapper` e o `JsonNode` do Jackson 2, que
  chegam ao classpath como dependência transitiva (o `jackson-databind` 2.21.5 vem pelo `eureka-client`, ADR-0006). É
  justamente o que o artigo quer impedir.

## Consequências

- Positivas:
  - A regra do Artigo XII fica coerente com o Jackson 3 que o projeto usa.
  - As anotações podem ser usadas onde forem necessárias, sem configuração global para contorná-las.
- Negativas e riscos aceitos:
  - A busca por `com.fasterxml.jackson` deixa de bastar na revisão: ela precisa separar `com.fasterxml.jackson.annotation`
    (permitido) do resto (proibido). O checklist de revisão registra essa ressalva.
  - Com o Jackson 2 no classpath por dependência transitiva, o autocompletar da IDE pode sugerir classes de
    `com.fasterxml.jackson.databind`. A revisão continua barrando essas importações.
