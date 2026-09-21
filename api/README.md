# Metalcor Procurement API

API em Java (Spring Boot, JdbcClient com SQL explícito, sem JPA). Empresas, fornecedores e dados são fictícios. Não é SAP.

Por enquanto expõe só leitura sobre as views do banco:

- `GET /api/v1/suppliers/scorecard`
- `GET /api/v1/invoices/exceptions` (filtros `resolution`, `exceptionType`, paginação `page` e `size`)

## Subir o banco

Na raiz do repositório (copie `.env.example` para `.env` se quiser mudar portas ou senhas):

```powershell
docker compose up -d
```

Isso sobe o PostgreSQL 17, aplica as migrações `db/init` (V1 a V10) com o Flyway e cria o usuário `metalcor_app`. Os dados fictícios vêm de `db/seed` (01 a 04).

## Rodar a API (perfil local)

Na pasta `api/`, com a porta do banco em `DB_PORT` (5434 na minha máquina, 5432 por padrão):

```powershell
$env:DB_PORT = "5434"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

O perfil `local` só define a senha de desenvolvimento (`metalcor_app_dev`); sem ele, `APP_DB_PASSWORD` é obrigatória. Variáveis lidas: `DB_HOST`, `DB_PORT`, `DB_NAME`, `APP_DB_USER`, `APP_DB_PASSWORD`.

- Swagger: http://localhost:8080/swagger-ui.html
- Health (com o banco): http://localhost:8080/actuator/health

## Testes

```powershell
mvn test
```

Precisam do Docker: sobem um PostgreSQL 17 descartável (Testcontainers), aplicam `db/init`, carregam `db/seed` e conectam como `metalcor_app`.
