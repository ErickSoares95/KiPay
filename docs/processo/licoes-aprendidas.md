# Lições aprendidas

> Registro dos achados de revisão. Cada achado vira uma regra, e a coluna "Onde foi aplicada" diz em que lugar a regra
> passou a ser verificada: `openspec/config.yaml`, CLAUDE.md, checklist (`docs/processo/checklist-revisao.md`),
> constituição ou teste automatizado. Os números de tarefa são os do `tasks.md` atual da change.

| Data | Change | Achado | Regra criada | Onde foi aplicada |
|---|---|---|---|---|
| 2026-10-08 | 001-abertura-de-conta | A decisão do chat "só o Accounts é resource server" não chegou aos artefatos: proposal, design e a tarefa 3.2 colocavam o Ledger validando JWT | Toda decisão do chat é refletida em todos os artefatos | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | A chave de idempotência era gravada no fim da transação. Dois pedidos simultâneos com a mesma chave podiam receber `409` de CPF em vez do mesmo `201` | A chave é reservada como primeiro comando da transação, com teste concorrente | Checklist (Artefatos e Tarefa) e teste automatizado (tarefa 5.4) |
| 2026-10-08 | 001-abertura-de-conta | O código de erro em concorrência dependia de qual constraint o PostgreSQL checava primeiro | A constraint garante a invariante; o código é decidido relendo o estado numa nova transação | Checklist (Artefatos) e teste automatizado (tarefa 5.4) |
| 2026-10-08 | 001-abertura-de-conta | A regra de quem nasce em 29/02 existia só na tarefa 4.3 | Regra de negócio fica na spec | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | Spec e API divergiam: a spec falava em "próprias contas" sem endpoint de listagem, e a chave em formato inválido não tinha código de erro no design | Spec, design e API dizem a mesma coisa | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | O export do realm do Keycloak inclui a chave privada e os secrets do realm, e a verificação só procurava client secret | Nenhum segredo em arquivo versionado | Checklist (Tarefa) e verificação da tarefa 2.4 (2.5 na numeração da revisão) |
| 2026-10-08 | 001-abertura-de-conta | O e-mail obrigatório no perfil do realm impediria o usuário `sem-email` de obter token, e o cenário "Identidade sem e-mail" não seria testável | Regra de negócio fica no serviço, e a infraestrutura de teste reproduz todos os cenários | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | A verificação do Spring Cloud Contract era só um contexto subindo com ele no classpath, e o springdoc ficava fora dos BOMs sem ADR | Compatibilidade verificada exercitando o uso real; versão fora dos BOMs só com ADR | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | Gauges de métrica sem requisito que os pedisse (tarefa 7.2) | Nada além do que os requisitos pedem | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | O cenário "Livro-razão indisponível por um período" não tinha teste | Todo cenário tem pelo menos um teste | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | A tarefa 1.3 verificava a ADR-0005, que só nascia na 1.4 (numeração da revisão) | A ordem das tarefas respeita as dependências | Checklist (Artefatos) |
| 2026-10-08 | 001-abertura-de-conta | A lista de termos novos do design (D14) estava incompleta | Todo termo de negócio novo do design está no glossário | Checklist (Artefatos) |
