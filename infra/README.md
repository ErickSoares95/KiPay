# Ambiente local (infra)

Subir tudo: copie `.env.example` para `.env` (o `.env` fica fora do Git) e rode `docker compose up` nesta pasta.

## Keycloak (realm `kipay`)

O Keycloak sobe em modo `start-dev --import-realm` e importa `keycloak/kipay-realm.json` (montado somente leitura em
`/opt/keycloak/data/import`). Não há volume de dados: cada `docker compose down -v && docker compose up` reimporta o
realm do arquivo e gera chaves novas, porque o arquivo não traz key providers.

- Console de administração: http://localhost:8080. O usuário e a senha do admin vêm do `.env`
  (`KEYCLOAK_ADMIN` e `KEYCLOAK_ADMIN_PASSWORD`).
- Health (porta de management 9000, não publicada): usado só pelo healthcheck do Compose.
- Imagem fixada em `KEYCLOAK_IMAGE` (sem `latest`).

### Credenciais locais (somente desenvolvimento)

| Usuário | Senha | E-mail |
|---|---|---|
| `ana` | `ana123` | `ana@kipay.local` |
| `bruno` | `bruno123` | `bruno@kipay.local` |
| `sem-email` | `sem-email123` | (nenhum) |

Clients (ambos públicos, sem segredo): `kipay-cli` (fluxo de senha, com audience `accounts` no access token) e
`accounts` (só audiência). O e-mail não é obrigatório no perfil de usuário do realm: a regra "abertura exige e-mail" é do
Accounts (`IDENTITY_EMAIL_MISSING`).

Obter um token:

```
curl -s -X POST http://localhost:8080/realms/kipay/protocol/openid-connect/token \
  -d grant_type=password -d client_id=kipay-cli -d username=ana -d password=ana123
```

### Como regenerar o `kipay-realm.json` (export)

1. Edite o realm no console (ou importe o arquivo atual num Keycloak com volume de dados nomeado, por exemplo
   `-v kcseed:/opt/keycloak/data`) e pare o servidor. O export exige o servidor parado, ou um container separado
   apontando para o mesmo volume.
2. Exporte num container separado, com a mesma imagem do `.env`:
   `docker run --rm -v kcseed:/opt/keycloak/data -v "<pasta-de-saida>:/tmp/out" <KEYCLOAK_IMAGE> export --realm kipay --users realm_file --dir /tmp/out`
   (na versão 26, `--users realm_file` só vale com `--dir`; o resultado é `kipay-realm.json`).
3. Limpe o arquivo antes de versionar:
   - remova o componente `org.keycloak.keys.KeyProvider` (`rsa-generated`, `rsa-enc-generated`, `hmac-generated-hs512`,
     `aes-generated`), para que a importação gere chaves novas;
   - troque as `credentials` de cada usuário (hashes em `secretData`) por `{"type": "password", "value": "<senha>",
     "temporary": false}`, com as senhas da tabela acima;
   - remova `clientAuthenticatorType`, `authenticationFlows`, `authenticatorConfig` e os `*Flow` do realm (o Keycloak
     recria os fluxos padrão na importação);
   - confira que `grep -i -E "privateKey|secret" kipay-realm.json` não encontra nada.
4. Salve em `infra/keycloak/kipay-realm.json`.
