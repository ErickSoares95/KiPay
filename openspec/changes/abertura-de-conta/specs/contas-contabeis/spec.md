# Spec Delta

## Purpose

Garante que toda conta aberta tenha exatamente uma conta contábil no livro-razão, onde os lançamentos vão acontecer, e
que a criação dessa conta contábil seja confirmada a quem abriu a conta.

## ADDED Requirements

### Requirement: Criação da conta contábil a partir da conta aberta
O sistema SHALL criar uma conta contábil em reais (BRL) para cada evento de conta aberta recebido, vinculada ao
identificador da conta. A conta contábil MUST nascer sem nenhum lançamento, com saldo derivado igual a zero, e MUST NOT
ter um campo de saldo editável.

#### Scenario: Evento de conta aberta recebido
- **WHEN** o livro-razão recebe um evento de conta aberta para uma conta que ainda não tem conta contábil
- **THEN** o sistema cria uma conta contábil em BRL vinculada a essa conta, sem lançamentos e com saldo zero

#### Scenario: Livro-razão indisponível por um período
- **WHEN** eventos de conta aberta são publicados enquanto o livro-razão está fora do ar
- **THEN** quando o livro-razão volta, ele processa os eventos pendentes e cria as contas contábeis correspondentes,
  sem perder nenhum

### Requirement: Uma única conta contábil por conta
O sistema SHALL manter no máximo uma conta contábil por conta. Processar o mesmo evento de conta aberta mais de uma
vez, ou dois eventos diferentes para a mesma conta, MUST NOT criar uma segunda conta contábil.

#### Scenario: Evento duplicado
- **WHEN** o mesmo evento de conta aberta é entregue duas vezes
- **THEN** existe uma única conta contábil para a conta e uma única confirmação de criação é publicada

#### Scenario: Entregas simultâneas do mesmo evento
- **WHEN** o mesmo evento de conta aberta é processado ao mesmo tempo por duas instâncias do livro-razão
- **THEN** existe no final uma única conta contábil para a conta

### Requirement: Confirmação da criação da conta contábil
O sistema SHALL publicar um evento de conta contábil criada somente depois que a criação foi confirmada. O evento MUST
trazer identificador único, momento da ocorrência, identificador do agregado, versão do schema, identificador da conta
contábil e identificador da conta de origem.

#### Scenario: Conta contábil criada gera confirmação
- **WHEN** uma conta contábil é criada com sucesso
- **THEN** um evento de conta contábil criada com o identificador da conta contábil e o da conta de origem é publicado,
  mesmo que o canal de eventos esteja indisponível no momento e só volte depois

#### Scenario: Criação desfeita não gera confirmação
- **WHEN** a criação da conta contábil falha e é desfeita
- **THEN** nenhum evento de conta contábil criada é publicado, e o evento de conta aberta volta a ser processado

### Requirement: Visibilidade de confirmações não publicadas
O sistema SHALL tornar visível para a operação a quantidade de confirmações de conta contábil criada gravadas e ainda
não publicadas no canal de eventos.

#### Scenario: Confirmações aguardando o canal de eventos
- **WHEN** contas contábeis são criadas enquanto o canal de eventos está indisponível
- **THEN** a quantidade de confirmações não publicadas cresce a cada conta contábil criada e volta a zero quando as
  confirmações são publicadas

### Requirement: Eventos inválidos não bloqueiam o processamento
O sistema SHALL separar para análise todo evento de conta aberta que não pode ser processado (schema desconhecido,
campos ausentes ou falha persistente), sem criar conta contábil e sem impedir o processamento dos eventos seguintes.

#### Scenario: Evento com versão de schema desconhecida
- **WHEN** chega um evento de conta aberta com uma versão de schema que o livro-razão não conhece
- **THEN** o evento é separado para análise, nenhuma conta contábil é criada e os eventos seguintes continuam sendo
  processados

#### Scenario: Falha temporária
- **WHEN** a criação da conta contábil falha por um erro temporário (por exemplo, o banco do livro-razão
  momentaneamente indisponível)
- **THEN** o processamento é tentado de novo e, se a falha persistir depois das tentativas, o evento é separado para
  análise

## Decisões de consistência

| Operação | Classificação | Comportamento na falha |
|---|---|---|
| Criar conta contábil | **CP** dentro do livro-razão: a conta contábil, o registro de evento processado e a confirmação a publicar são gravados juntos ou nada é gravado | Se a gravação falhar, nada fica registrado e o evento é processado de novo. Falhas persistentes separam o evento para análise. A conta continua PENDENTE no Accounts |
| Unicidade da conta contábil por conta | **CP** | Entregas repetidas ou simultâneas resultam numa única conta contábil. A repetição é reconhecida e ignorada |
| Confirmar a criação ao Accounts | **Eventualmente consistente** (AP em relação ao Accounts) | A confirmação fica guardada e é publicada quando o canal de eventos estiver disponível, pelo menos uma vez. O Accounts trata confirmações repetidas como uma só |
