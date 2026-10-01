# Metalcor Procurement API

API em Java 21 (Spring Boot, JdbcClient com SQL explícito, sem JPA). Empresas, fornecedores e dados são fictícios. Não é SAP.

## O que a API expõe

Prefixo `/api/v1`. A documentação completa está no Swagger (perfil `local`).

- **Leitura:** fornecedores (scorecard), exceções de fatura, requisições, pedidos, recebimentos, faturas e pagamentos por id.
- **Escrita (fluxo completo):** criar, enviar e decidir requisição; emitir pedido a partir da requisição; registrar recebimento e fatura em um pedido (com three-way match); aprovar e pagar fatura. Cada escrita roda em uma transação e define `app.current_user_id` para a auditoria.
- **Dashboard:** KPIs, gasto por mês, resumo de exceções, pagamentos atrasados, faturas paradas, scorecard de fornecedores e exportação `export.xlsx`.
- **Copiloto:** `POST /copilot/query` gera um SELECT somente leitura a partir de uma pergunta em linguagem natural (Ollama local ou Gemini), com validação e limite de perguntas por minuto.

As escritas exigem o cabeçalho provisório `X-User-Id` (veja "Limitações" no README da raiz).

## Subir o banco

Na raiz do repositório (copie `.env.example` para `.env` se quiser mudar portas ou senhas):

```powershell
docker compose up -d
```

Isso sobe o PostgreSQL 17, aplica as migrações `db/init` (V1 a V11) com o Flyway e cria os usuários `metalcor_app` e `metalcor_readonly`. Os dados fictícios vêm de `db/seed` (01 a 04), carregados à parte (veja o README da raiz).

## Rodar a API (perfil local)

Na pasta `api/`, com a porta do banco em `DB_PORT` (5434 na minha máquina, 5432 por padrão):

```powershell
$env:DB_PORT = "5434"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

O perfil `local` só define senhas de desenvolvimento e a origem do CORS; sem ele, `APP_DB_PASSWORD` e `READONLY_DB_PASSWORD` são obrigatórias. O perfil `prod` não tem padrões e desliga o Swagger.

- Swagger: http://localhost:8080/swagger-ui.html
- Health (com o banco): http://localhost:8080/actuator/health

## Maven Wrapper no Windows com espaço no nome de usuário

**Sintoma:** `.\mvnw.cmd test` ou `.\mvnw.cmd spring-boot:run` falham direto no PowerShell com:

```
'C:\Users\renzo' is not recognized as an internal or external command, operable program or batch file.
Cannot start maven from wrapper
```

**Quando acontece:** em Windows cujo nome de usuário (`%USERPROFILE%`) contém espaço, por exemplo `C:\Users\nome sobrenome`. O `mvnw.cmd` quebra o caminho no espaço, trata `C:\Users\nome` como o comando e `sobrenome\...` como argumento. É um bug conhecido do maven-wrapper com espaço no `%USERPROFILE%`.

**Contorno:** defina `MAVEN_USER_HOME` para um caminho sem espaço antes de usar o wrapper (vale para `test`, `spring-boot:run` ou qualquer outro goal). O wrapper passa a baixar e guardar o Maven nessa pasta.

```powershell
$env:MAVEN_USER_HOME = "D:\.mvnwrapper"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

A variável vale só para a sessão atual do terminal; é preciso definir de novo a cada nova sessão, a menos que você a crie como variável de ambiente do usuário ou do sistema no Windows.

Isso afeta só o `mvnw.cmd` (o wrapper). O `mvn` instalado globalmente ou o embutido no IntelliJ funcionam normalmente com o mesmo usuário.

## Variáveis de ambiente

| Variável | Para quê | Padrão |
|---|---|---|
| `DB_HOST` | Host do PostgreSQL | `localhost` |
| `DB_PORT` | Porta do PostgreSQL | `5432` |
| `DB_NAME` | Nome do banco | `metalcor` |
| `APP_DB_USER` | Usuário da API (leitura e escrita) | `metalcor_app` |
| `APP_DB_PASSWORD` | Senha do usuário da API | sem padrão (no perfil `local`: `metalcor_app_dev`) |
| `READONLY_DB_USER` | Usuário somente leitura do copiloto | `metalcor_readonly` |
| `READONLY_DB_PASSWORD` | Senha do usuário somente leitura | sem padrão (no perfil `local`: `metalcor_readonly_dev`) |
| `APP_CORS_ALLOWED_ORIGIN` | Origem do frontend liberada no CORS | `http://localhost:5173` no perfil `local`; obrigatória no `prod` |
| `COPILOT_PROVIDER` | `ollama` ou `gemini` | `ollama` (`gemini` no perfil `prod`) |
| `GEMINI_API_KEY` | Chave da API do Gemini (só com `gemini`) | vazio |
| `GEMINI_MODEL` | Modelo do Gemini | `gemini-flash-lite-latest` |
| `OLLAMA_BASE_URL` | Endereço do Ollama (só com `ollama`) | `http://localhost:11434` |
| `OLLAMA_MODEL` | Modelo do Ollama | `qwen2.5-coder:7b` |

Há ainda `COPILOT_RATE_LIMIT_PER_MINUTE` (perguntas por usuário por minuto, padrão 10). A API conecta sempre como `metalcor_app` (e o copiloto como `metalcor_readonly`), nunca como o dono do banco.

## Testes

```powershell
mvn test
```

Precisam do Docker: sobem um PostgreSQL 17 descartável (Testcontainers), aplicam `db/init`, carregam `db/seed` e conectam como `metalcor_app`.
