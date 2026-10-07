# Conceitos do domínio — Carteira Digital

## 1. O que é uma carteira digital, do ponto de vista do Banco Central

### Instituição de pagamento, não banco

Para o Banco Central, carteiras digitais como PicPay, Mercado Pago ou PagBank são **instituições de pagamento (IP)**. A modalidade que corresponde ao projeto é a de **Emissor de Moeda Eletrônica (EME)**, definida na Resolução BCB 80 (2021): instituição que gerencia conta de pagamento pré-paga do usuário final, permitindo pagar ou transferir com base em recursos aportados previamente nessa conta.

### Três conceitos que viram regra no projeto

- **Conta de pagamento pré-paga, não conta corrente** — o cliente só gasta o que colocou antes; não existe cheque especial. Regra no Ledger: **o saldo nunca pode ficar negativo**
- **Moeda eletrônica não é depósito bancário** — não rende por natureza e fica mantida em conta de pagamento pré-paga
- **Dinheiro dos clientes separado do dinheiro da empresa (salvaguarda)** — os saldos dos clientes devem ficar em espécie numa conta da instituição no Banco Central (CCME) ou em títulos públicos federais. A carteira não pode usar o saldo dos clientes para pagar as próprias contas. É a base da conciliação (tópico 7)

## 2. Como o dinheiro funciona por dentro: o livro-razão

### A ideia central

O saldo do cliente é uma **dívida da carteira com ele**: se a Ana tem R$ 100, a empresa deve R$ 100 à Ana. Contabilmente, um **passivo**. O dinheiro guardado pela empresa (CCME ou títulos) é um **ativo**.

### Partidas dobradas

Toda transação registra de onde o dinheiro veio e para onde foi: pelo menos dois lançamentos, que sempre se equilibram.

| Operação | Lançamentos | Efeito |
|---|---|---|
| Ana recebe Pix de R$ 100 de outro banco | Débito em Disponibilidades R$ 100 / Crédito na Conta Ana R$ 100 | Entrou dinheiro na empresa e a dívida com a Ana aumentou |
| Ana transfere R$ 30 ao Bruno (ambos na carteira) | Débito na Conta Ana R$ 30 / Crédito na Conta Bruno R$ 30 | Nenhum dinheiro sai da empresa: só muda a quem ela deve |
| Bruno envia Pix de R$ 20 para outro banco | Débito na Conta Bruno R$ 20 / Crédito em Disponibilidades R$ 20 | Saiu dinheiro da empresa e a dívida com o Bruno diminuiu |

Resultado: Disponibilidades = R$ 80; Ana (R$ 70) + Bruno (R$ 10) = R$ 80.

### O invariante do sistema

**Dinheiro guardado pela empresa = soma dos saldos dos clientes.** Se não bater, existe bug ou fraude. A transferência interna é só troca de dono dentro do livro-razão — por isso toda movimentação fica no Ledger, numa única transação ACID (ADR-0001).

### Por que o saldo não é um campo editável

Com `saldo = saldo - 30` perde-se o histórico e não há como provar como se chegou ao valor. Com lançamentos imutáveis, o saldo pode ser reconstruído em qualquer data, e erros são corrigidos com **estorno** (novo lançamento inverso), nunca apagando o original.

## 3. Saldo contábil, saldo disponível e reserva

### Dois saldos

- **Saldo contábil** — soma de todos os lançamentos efetivados
- **Saldo disponível** — saldo contábil menos as **reservas** (holds) em andamento

### Reserva

Funciona como a autorização do cartão no posto de gasolina: o valor fica bloqueado antes da cobrança final. Ao iniciar uma transferência de R$ 30, o Ledger cria uma reserva: a Ana fica com R$ 70 disponíveis e R$ 100 contábeis.
- Aprovada → a reserva vira lançamento (**liquidação**)
- Recusada (ex: antifraude) → a reserva é **liberada** (compensação da saga)

A reserva resolve concorrência: duas transferências simultâneas de R$ 80 com saldo de R$ 100 não podem passar as duas.

## 4. O ciclo de vida de uma transferência

Uma transferência passa por estados, e cada estado é um passo da saga:

```
CRIADA → RESERVADA → EM_ANALISE → LIQUIDADA
                         ↓
                     RECUSADA (reserva liberada)
```

Uma transferência liquidada ainda pode ser **ESTORNADA** (ex: devolução por fraude via MED, tópico 6). Guardar os estados permite responder "onde está meu dinheiro?" e retomar uma saga interrompida por uma queda.

## 5. Idempotência: o problema do "será que debitou?"

### Cenário

O app envia a transferência, o servidor processa, mas a resposta se perde (o celular saiu do 4G). O app não sabe se deu certo e tenta de novo. Sem proteção, a Ana paga duas vezes.

### Solução

- O app gera uma **chave de idempotência** (UUID) para aquela intenção de transferir e a envia em todas as tentativas
- O servidor guarda a chave com o resultado; se a mesma chave chegar de novo, devolve o resultado guardado sem repetir a operação
- O mesmo vale para eventos: consumidor que recebe o mesmo evento duas vezes (normal em mensageria) não pode lançar duas vezes

## 6. O Pix por dentro

### Peças do sistema

- **SPI (Sistema de Pagamentos Instantâneos)** — infraestrutura do Banco Central que liquida transferências entre instituições diferentes, usando chaves Pix como "apelido" da conta
- **DICT (Diretório de Identificadores de Contas Transacionais)** — traduz a chave para os dados da conta. Pessoa física: até 5 chaves por conta; pessoa jurídica: até 20. O Bacen limita as consultas de cada participante e penaliza consultas a chaves inexistentes, para impedir varredura de dados (rate limiting real)

### Limites

- A regulamentação exige limites por transação e por período (diurno/noturno), com limite noturno pré-estabelecido para pessoas físicas
- Padrão noturno (20h às 6h): R$ 1.000 entre pessoas físicas
- Dispositivo não cadastrado: R$ 200 por operação e R$ 1.000 por dia
- No projeto: regras para o serviço de Antifraude/Limites

### MED (Mecanismo Especial de Devolução)

- A vítima relata a fraude; o banco dela abre o MED; o banco do suposto golpista bloqueia os valores
- Análise em até 7 dias corridos; comprovada a fraude, devolução em até 96 horas
- **MED 2.0**: rastreia o caminho do dinheiro pelo DICT e bloqueia saldo em até cinco camadas de contas subsequentes, em qualquer instituição
- **Mudança recente**: prazo para contestar devoluções fraudulentas passou de 30 para 80 dias (Instrução Normativa BCB nº 766, em vigor desde 1º de setembro de 2026)

Para o projeto: o saldo disponível pode ser **bloqueado por ordem externa** (bloqueio cautelar), e o estorno é um fluxo de primeira classe, não um caso raro.

## 7. Conciliação

Conciliar é conferir se dois registros independentes contam a mesma história.

- **Interna** — o invariante do tópico 2 (soma dos saldos = dinheiro guardado) e "todo débito tem seu crédito"
- **Externa** — o que o livro-razão diz que entrou e saiu via Pix deve bater com o extrato do SPI

Na prática: job diário (Spring Batch) que compara registros, aponta divergências e gera relatório. Divergência típica: Pix liquidado pelo SPI mas não registrado no sistema por uma queda no meio do processo.

## 8. Onboarding, KYC e antifraude

- **KYC (Know Your Customer)** — validar quem abre a conta: CPF válido e ativo, maioridade, dados consistentes (spec 001)
- **Limites por perfil** — conta nova tem limites menores, que crescem com o tempo de relacionamento
- **Regras típicas de antifraude** — muitas transferências em pouco tempo (velocity), valor muito acima do habitual, horário incomum, destino recém-criado, dispositivo novo
- **Contas "laranja"** — abertas para receber dinheiro de golpe; por isso o recebimento também é monitorado, não só o envio

## 9. O que vamos construir (e o que fica de fora)

| Conceito real | No nosso projeto |
|---|---|
| Conta de pagamento pré-paga | Accounts + Ledger, saldo nunca negativo |
| Livro-razão de partidas dobradas | Ledger com lançamentos imutáveis e estornos |
| Reserva e liquidação | Saga de transferência no Transfers |
| Pix entre instituições (SPI) | "SPI simulado": serviço fake que liquida com atraso e falhas configuráveis |
| DICT e chaves | Serviço Pix com cadastro de chaves (até 5 por PF) e rate limiting na consulta |
| Limites diurno/noturno | Regras no Antifraude/Limites |
| MED e bloqueio cautelar | Fluxo de bloqueio e estorno (fase avançada) |
| Salvaguarda e conciliação | Job de conciliação verificando o invariante |
| Regulação, auditoria do BC, títulos públicos | Fora de escopo |
