# Spec Delta

## Purpose

Permite que uma pessoa física autenticada abra uma conta de pagamento pré-paga na carteira digital e acompanhe o
ciclo de vida dessa conta, da abertura como PENDENTE até a ativação confirmada pelo livro-razão.

## ADDED Requirements

### Requirement: Abertura de conta por pessoa física autenticada
O sistema SHALL permitir que uma pessoa autenticada peça a abertura de uma conta informando nome completo, CPF e data
de nascimento. A conta MUST nascer com status PENDENTE, vinculada à identidade de quem a pediu, e o e-mail do titular
MUST ser obtido da identidade autenticada, sem ser pedido no cadastro. A resposta MUST trazer o identificador da conta,
o status, o CPF mascarado, se a conta pode movimentar dinheiro e o momento da abertura.

#### Scenario: Abertura aceita
- **WHEN** uma pessoa autenticada, maior de idade e ainda sem conta, envia um pedido de abertura com nome completo,
  CPF válido e ainda não cadastrado, data de nascimento e uma chave de idempotência nova
- **THEN** o sistema cria a conta com status PENDENTE, guarda o e-mail da identidade junto com o titular e responde com
  sucesso, trazendo o identificador, o status, o CPF mascarado, se a conta pode movimentar dinheiro e o momento da
  abertura

#### Scenario: Identidade sem e-mail
- **WHEN** uma pessoa autenticada cuja identidade não traz e-mail envia um pedido de abertura válido
- **THEN** o sistema recusa o pedido com o código de erro de identidade sem e-mail e não cria conta

#### Scenario: Pedido sem autenticação
- **WHEN** um pedido de abertura chega sem credencial ou com credencial inválida ou expirada
- **THEN** o sistema recusa o pedido com o código de erro de não autenticado e não cria nenhuma conta

#### Scenario: Dependência do livro-razão indisponível
- **WHEN** um pedido de abertura válido chega enquanto o livro-razão ou o canal de eventos está indisponível
- **THEN** o sistema aceita o pedido e cria a conta como PENDENTE, e a ativação acontece depois que a dependência
  voltar

#### Scenario: Banco do Accounts indisponível na abertura
- **WHEN** um pedido de abertura válido chega enquanto o banco do próprio Accounts está indisponível
- **THEN** o sistema recusa o pedido com o código de erro de serviço temporariamente indisponível, não grava nada
  (nem a chave de idempotência) e o cliente pode repetir o pedido com a mesma chave

### Requirement: Validação do CPF
O sistema SHALL recusar um pedido de abertura cujo CPF não seja válido, com um código de erro de negócio estável que
identifique o motivo. Aceitam-se CPF com ou sem pontuação, sempre normalizado para os 11 dígitos. A validade é
verificada pelo formato e pelos dígitos verificadores. A situação do CPF na Receita Federal não é consultada.

#### Scenario: CPF com dígitos verificadores incorretos
- **WHEN** o pedido traz um CPF de 11 dígitos cujos dígitos verificadores não conferem
- **THEN** o sistema recusa o pedido com o código de erro de CPF inválido e não cria conta

#### Scenario: CPF com todos os dígitos iguais
- **WHEN** o pedido traz um CPF formado por um único dígito repetido (por exemplo, 111.111.111-11)
- **THEN** o sistema recusa o pedido com o código de erro de CPF inválido

#### Scenario: CPF com pontuação
- **WHEN** o pedido traz um CPF válido no formato 000.000.000-00
- **THEN** o sistema aceita o CPF e o trata como o mesmo CPF informado só com dígitos

#### Scenario: CPF ausente ou com tamanho errado
- **WHEN** o pedido não traz CPF, ou traz um valor que não tem 11 dígitos depois da normalização
- **THEN** o sistema recusa o pedido como dados inválidos e indica o campo com problema

### Requirement: Dados cadastrais obrigatórios
O sistema SHALL exigir nome completo, CPF e data de nascimento em todo pedido de abertura, e SHALL recusar o pedido em
que falte algum deles ou em que algum tenha formato inválido, indicando cada campo com problema. Nenhum outro dado
pessoal MUST ser pedido no cadastro. O nome completo MUST ser guardado como informado, sem os espaços nas pontas.

#### Scenario: Nome completo ausente ou em branco
- **WHEN** o pedido de abertura não traz o nome completo, ou o traz só com espaços
- **THEN** o sistema recusa o pedido como dados inválidos, indica o campo do nome e não cria conta

#### Scenario: Data de nascimento ausente ou inválida
- **WHEN** o pedido de abertura não traz a data de nascimento, traz uma data em formato inválido ou uma data futura
- **THEN** o sistema recusa o pedido como dados inválidos, indica o campo da data de nascimento e não cria conta

#### Scenario: Vários campos com problema
- **WHEN** o pedido de abertura tem mais de um campo ausente ou inválido
- **THEN** o sistema recusa o pedido e indica todos os campos com problema na mesma resposta

### Requirement: Idade mínima
O sistema SHALL aceitar a abertura somente para quem tem 18 anos completos na data do pedido, considerando a data
civil no horário de Brasília. Um pedido de quem tem menos de 18 anos MUST ser recusado com um código de erro de negócio
estável.

#### Scenario: Menor de idade
- **WHEN** o pedido traz uma data de nascimento em que o titular ainda não completou 18 anos
- **THEN** o sistema recusa o pedido com o código de erro de titular menor de idade e não cria conta

#### Scenario: Aniversário de 18 anos no dia do pedido
- **WHEN** o pedido é feito exatamente no dia em que o titular completa 18 anos
- **THEN** o sistema aceita o pedido

#### Scenario: Véspera do aniversário de 18 anos
- **WHEN** o pedido é feito na véspera do dia em que o titular completa 18 anos
- **THEN** o sistema recusa o pedido com o código de erro de titular menor de idade

#### Scenario: Nascido em 29 de fevereiro
- **WHEN** o titular nasceu em 29 de fevereiro e o ano em que completa 18 anos não é bissexto
- **THEN** o sistema considera que ele completa 18 anos em 1º de março: recusa o pedido feito em 28 de fevereiro e
  aceita o pedido feito em 1º de março

### Requirement: CPF único
O sistema SHALL permitir no máximo uma conta não encerrada por CPF. Uma nova abertura para um CPF que já tem conta não
encerrada MUST ser recusada com um código de erro de negócio estável, mesmo quando os pedidos chegam ao mesmo tempo.
Depois do encerramento, o CPF pode abrir uma nova conta, e a conta encerrada e os dados dela são mantidos.

#### Scenario: Segunda abertura pela mesma identidade
- **WHEN** a identidade dona de uma conta não encerrada pede, com outra chave de idempotência, uma nova abertura para o
  mesmo CPF
- **THEN** o sistema recusa o pedido com o código de erro de conta já aberta e a conta existente não muda

#### Scenario: Reabertura depois do encerramento
- **WHEN** a única conta de um CPF está encerrada e a mesma identidade pede uma nova abertura para esse CPF
- **THEN** o sistema cria uma nova conta PENDENTE, e a conta encerrada continua existindo com os dados e o status
  inalterados

#### Scenario: Pedidos simultâneos da mesma identidade com o mesmo CPF
- **WHEN** a mesma identidade envia, ao mesmo tempo, dois pedidos de abertura para o mesmo CPF com chaves de
  idempotência diferentes
- **THEN** exatamente um deles cria a conta e o outro é recusado com o código de erro de conta já aberta

#### Scenario: Pedidos simultâneos de identidades diferentes com o mesmo CPF
- **WHEN** duas identidades diferentes, ambas sem conta, enviam ao mesmo tempo pedidos de abertura para o mesmo CPF
- **THEN** exatamente um deles cria a conta e o outro é recusado com o código de erro de CPF já cadastrado

#### Scenario: Mesmo CPF com e sem pontuação
- **WHEN** já existe uma conta não encerrada para um CPF e a mesma identidade envia um pedido com o mesmo CPF escrito
  em outro formato
- **THEN** o sistema reconhece que é o mesmo CPF e recusa o pedido com o código de erro de conta já aberta

### Requirement: Vínculo entre identidade e CPF
O sistema SHALL ligar cada identidade a um único CPF e cada CPF a uma única identidade, a partir da primeira abertura
aceita. Um pedido que quebre esse vínculo MUST ser recusado com um código de erro de negócio estável, também quando os
pedidos chegam ao mesmo tempo.

#### Scenario: Mesma identidade com outro CPF
- **WHEN** uma identidade que já abriu conta para um CPF pede abertura para outro CPF
- **THEN** o sistema recusa o pedido com o código de erro de identidade já vinculada a outro CPF e não cria conta

#### Scenario: Outra identidade com o mesmo CPF
- **WHEN** uma identidade diferente pede abertura para um CPF já ligado a outra identidade
- **THEN** o sistema recusa o pedido com o código de erro de CPF já cadastrado, sem revelar dados da outra identidade

#### Scenario: Outra identidade com CPF de conta encerrada
- **WHEN** o CPF de uma conta encerrada é usado num pedido de abertura por uma identidade diferente da original
- **THEN** o sistema recusa o pedido com o código de erro de CPF já cadastrado

#### Scenario: Pedidos simultâneos da mesma identidade com CPFs diferentes
- **WHEN** uma identidade sem conta envia, ao mesmo tempo, dois pedidos de abertura com CPFs diferentes e chaves de
  idempotência diferentes
- **THEN** exatamente um deles cria a conta e o outro é recusado com o código de erro de identidade já vinculada a
  outro CPF

### Requirement: Idempotência da abertura
O sistema SHALL exigir uma chave de idempotência em todo pedido de abertura. Repetir o pedido com a mesma chave, pela
mesma identidade, MUST devolver o mesmo resultado do primeiro pedido, sem criar outra conta nem publicar outro evento
de conta aberta. Reusar a chave com conteúdo diferente MUST ser recusado.

#### Scenario: Repetição com a mesma chave
- **WHEN** um pedido de abertura aceito é reenviado com a mesma chave de idempotência e o mesmo conteúdo
- **THEN** o sistema responde com o mesmo resultado do primeiro pedido (mesmo identificador de conta) e continua
  existindo uma única conta e um único evento de conta aberta

#### Scenario: Repetição de um pedido recusado
- **WHEN** um pedido recusado por regra de negócio (por exemplo, CPF já cadastrado ou titular menor de idade) é
  reenviado com a mesma chave e o mesmo conteúdo
- **THEN** o sistema devolve a mesma recusa, com o mesmo código de erro

#### Scenario: Repetição de um pedido recusado por dados inválidos
- **WHEN** um pedido recusado como dados inválidos é reenviado com a mesma chave e o mesmo conteúdo
- **THEN** o sistema devolve a mesma recusa de dados inválidos, e corrigir o pedido exige uma nova chave de
  idempotência

#### Scenario: Repetição de recusa depois de a condição mudar
- **WHEN** um pedido recusado por titular menor de idade é reenviado com a mesma chave e o mesmo conteúdo depois de o
  titular completar 18 anos
- **THEN** o sistema devolve a mesma recusa, e uma nova abertura exige uma nova chave de idempotência

#### Scenario: Mesma chave com conteúdo diferente
- **WHEN** chega um pedido com uma chave de idempotência já usada pela mesma identidade, mas com conteúdo diferente
- **THEN** o sistema recusa o pedido com o código de erro de chave de idempotência reutilizada e nenhuma conta é criada
  ou alterada

#### Scenario: Pedido sem chave de idempotência
- **WHEN** um pedido de abertura chega sem chave de idempotência
- **THEN** o sistema recusa o pedido com o código de erro de chave de idempotência ausente e não cria conta

#### Scenario: Chave de idempotência em formato inválido
- **WHEN** um pedido de abertura chega com uma chave de idempotência em formato inválido
- **THEN** o sistema recusa o pedido com o código de erro de chave de idempotência inválida e não cria conta

#### Scenario: Repetições simultâneas com a mesma chave
- **WHEN** dois pedidos idênticos com a mesma chave de idempotência chegam ao mesmo tempo
- **THEN** existe no final uma única conta, e as duas respostas trazem o mesmo identificador de conta ou uma delas
  pede ao cliente que tente de novo

#### Scenario: Mesma chave usada por identidades diferentes
- **WHEN** duas identidades diferentes usam o mesmo valor de chave de idempotência em pedidos próprios
- **THEN** o sistema trata os pedidos como independentes, sem que um devolva o resultado do outro

### Requirement: Ativação após a confirmação do livro-razão
O sistema SHALL mudar a conta de PENDENTE para ATIVA somente depois de receber a confirmação de que a conta contábil
correspondente foi criada no livro-razão. Receber a mesma confirmação mais de uma vez MUST NOT mudar o resultado. Se a
confirmação não chegar, a conta MUST continuar PENDENTE, sem cancelamento automático.

#### Scenario: Confirmação recebida
- **WHEN** chega a confirmação de criação da conta contábil de uma conta PENDENTE
- **THEN** a conta passa a ATIVA e o momento da ativação fica registrado

#### Scenario: Confirmação duplicada
- **WHEN** a mesma confirmação de criação da conta contábil chega uma segunda vez para uma conta já ATIVA
- **THEN** a conta continua ATIVA, o momento da ativação não muda e nada mais é alterado

#### Scenario: Confirmação para conta desconhecida
- **WHEN** chega uma confirmação de criação de conta contábil que não corresponde a nenhuma conta do Accounts
- **THEN** o sistema não cria nem altera nenhuma conta e separa a mensagem para análise, sem bloquear o processamento
  das demais

#### Scenario: Confirmação ainda não recebida
- **WHEN** a conta foi aberta e a confirmação do livro-razão ainda não chegou, mesmo depois de muito tempo
- **THEN** a conta continua PENDENTE e não é cancelada nem alterada automaticamente

### Requirement: Visibilidade de contas pendentes
O sistema SHALL tornar visível para a operação a quantidade de contas PENDENTE há mais de 10 minutos, contados a partir
da abertura. O limite de 10 minutos MUST poder ser ajustado sem mudar o código.

#### Scenario: Conta pendente além do limite
- **WHEN** uma conta está PENDENTE há mais de 10 minutos
- **THEN** ela entra na contagem de contas pendentes além do limite

#### Scenario: Conta pendente dentro do limite
- **WHEN** uma conta está PENDENTE há menos de 10 minutos
- **THEN** ela não entra na contagem de contas pendentes além do limite

#### Scenario: Conta ativada sai da contagem
- **WHEN** uma conta que estava PENDENTE além do limite é ativada
- **THEN** ela deixa de entrar na contagem de contas pendentes além do limite

### Requirement: Conta não ativa não movimenta dinheiro
O sistema SHALL informar, para qualquer conta, se ela pode movimentar dinheiro. Só uma conta ATIVA pode. As operações
de movimentação criadas em changes futuras MUST consultar essa regra e recusar contas que não estejam ATIVAS.

#### Scenario: Conta pendente
- **WHEN** se verifica se uma conta PENDENTE pode movimentar dinheiro
- **THEN** a resposta é negativa

#### Scenario: Conta ativa
- **WHEN** se verifica se uma conta ATIVA pode movimentar dinheiro
- **THEN** a resposta é positiva

#### Scenario: Conta encerrada
- **WHEN** se verifica se uma conta ENCERRADA pode movimentar dinheiro
- **THEN** a resposta é negativa

### Requirement: Consulta da própria conta
O sistema SHALL permitir que uma pessoa autenticada consulte uma conta aberta por ela, informando o identificador da
conta, e receba o status, se a conta pode movimentar dinheiro e o momento da abertura e da ativação. O CPF MUST
aparecer mascarado na resposta. Uma conta de outra identidade MUST ser tratada como inexistente. Não há listagem de
contas nesta capacidade.

#### Scenario: Consulta da própria conta
- **WHEN** o titular autenticado consulta, pelo identificador, uma conta aberta por ele
- **THEN** o sistema devolve o identificador, o status atual, se a conta pode movimentar dinheiro, o CPF mascarado, o
  momento da abertura e o da ativação, quando houver

#### Scenario: Consulta de conta de outro titular
- **WHEN** uma pessoa autenticada consulta o identificador de uma conta aberta por outra identidade
- **THEN** o sistema responde que a conta não foi encontrada, sem revelar que ela existe

#### Scenario: Consulta de conta inexistente
- **WHEN** uma pessoa autenticada consulta um identificador de conta que não existe
- **THEN** o sistema responde que a conta não foi encontrada, com o código de erro de conta não encontrada

#### Scenario: Consulta com identificador de conta inválido
- **WHEN** uma pessoa autenticada consulta uma conta com um identificador que não tem o formato de identificador de conta
- **THEN** o sistema recusa o pedido como dados inválidos, indicando o campo do identificador

#### Scenario: Consulta sem autenticação
- **WHEN** um pedido de consulta chega sem credencial ou com credencial inválida ou expirada
- **THEN** o sistema recusa o pedido com o código de erro de não autenticado, sem revelar se a conta existe

#### Scenario: Banco do Accounts indisponível na consulta
- **WHEN** uma pessoa autenticada consulta uma conta enquanto o banco do próprio Accounts está indisponível
- **THEN** o sistema responde com o código de erro de serviço temporariamente indisponível, em vez de devolver dado
  desatualizado

### Requirement: Evento de conta aberta
O sistema SHALL publicar um evento de conta aberta para toda conta criada, somente se a criação foi confirmada, e
nunca antes dela. O evento MUST trazer identificador único, momento da ocorrência, identificador da conta e versão do
schema, e MUST NOT trazer dados pessoais (CPF, nome, e-mail).

#### Scenario: Conta criada gera evento
- **WHEN** uma conta é criada com sucesso
- **THEN** um evento de conta aberta com o identificador da conta é publicado, mesmo que o canal de eventos esteja
  indisponível no momento da criação e só volte depois

#### Scenario: Criação desfeita não gera evento
- **WHEN** a criação da conta falha e é desfeita
- **THEN** nenhum evento de conta aberta é publicado

#### Scenario: Evento sem dados pessoais
- **WHEN** um evento de conta aberta é publicado
- **THEN** ele não contém CPF, nome nem nenhum outro dado pessoal do titular

### Requirement: Visibilidade de eventos não publicados
O sistema SHALL tornar visível para a operação a quantidade de eventos de conta aberta gravados e ainda não publicados
no canal de eventos.

#### Scenario: Eventos aguardando o canal de eventos
- **WHEN** contas são abertas enquanto o canal de eventos está indisponível
- **THEN** a quantidade de eventos não publicados cresce a cada conta aberta e volta a zero quando os eventos são
  publicados

### Requirement: Proteção de dados pessoais
O sistema SHALL tratar o CPF e os demais dados pessoais do titular conforme a LGPD. Eles MUST NOT aparecer em texto
claro em registros de log, mensagens de erro ou eventos.

#### Scenario: Log de abertura
- **WHEN** um pedido de abertura é processado, aceito ou recusado
- **THEN** os registros de log produzidos não contêm o CPF nem outros dados pessoais em texto claro

#### Scenario: Erro de CPF já cadastrado
- **WHEN** um pedido é recusado por CPF já cadastrado
- **THEN** a mensagem de erro não repete o CPF em texto claro

## Decisões de consistência

| Operação | Classificação | Comportamento na falha |
|---|---|---|
| Abrir conta (cadastro) | **AP** em relação ao livro-razão: o pedido é aceito sem depender do Ledger nem do canal de eventos | Se o Ledger ou o canal de eventos estiver indisponível, a conta é criada como PENDENTE e o evento fica guardado para publicação posterior. Se o banco do próprio Accounts estiver indisponível, o pedido é recusado como erro temporário e pode ser repetido com a mesma chave de idempotência |
| CPF único | **CP** | Em pedidos concorrentes, só um vence, e o outro é recusado com CPF já cadastrado ou conta já aberta. Nunca existem duas contas não encerradas com o mesmo CPF |
| Vínculo entre identidade e CPF | **CP** | Em pedidos concorrentes, só um vence. Nunca existe uma identidade ligada a dois CPFs, nem um CPF ligado a duas identidades |
| Idempotência da abertura | **CP** | A chave e o resultado são gravados de forma atômica junto com a conta. Se o banco falhar, nada é gravado e o cliente repete com a mesma chave |
| Ativar conta | **CP** na transição de status, **eventualmente consistente** em relação à abertura | A conta só vira ATIVA com a confirmação do livro-razão. Enquanto a confirmação não chega, ela continua PENDENTE e não movimenta dinheiro, sem cancelamento automático. Confirmações repetidas não mudam o resultado. As contas PENDENTE há mais de 10 minutos ficam visíveis para a operação |
| Consultar a própria conta | **CP** (lê o dado do próprio Accounts) | Se o banco do Accounts estiver indisponível, a consulta falha com erro temporário em vez de devolver dado desatualizado. Logo após a abertura, a consulta pode mostrar PENDENTE até a ativação chegar |
| Verificar se a conta pode movimentar | **CP** | Na dúvida (conta inexistente ou banco indisponível), a resposta é que a conta não pode movimentar |
