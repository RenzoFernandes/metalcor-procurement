# Metalcor Procurement

Mini-ERP de compras (procure-to-pay) com Java, PostgreSQL e React. Projeto de estudo para portfólio. [English version below](#english).

> **Aviso:** a Metalcor Autopeças, todos os fornecedores, materiais, usuários e valores são **100% fictícios**, criados para fins de portfólio. Os preços são estimativas e qualquer semelhança com empresas reais é coincidência. Este projeto **não é SAP** e não deve ser apresentado como tal: ele é inspirado em como ERPs organizam o processo de compras.

## Visão geral

Simula o ciclo de compras de uma metalúrgica de autopeças:

**requisição → aprovação (regra por valor) → pedido → recebimento → fatura → conta a pagar**

O ponto central é o **three-way match**: cada fatura é cruzada com o pedido e o recebimento, usando tolerâncias configuráveis (padrão 2% de preço e 5% de quantidade). Faturas fora da tolerância ficam bloqueadas até alguém resolver. O projeto se inspira em conceitos dos módulos de compras (MM) e financeiro (FI) de ERPs, como documentos com cabeçalho e itens, numeração por intervalos, aprovação e trilha de auditoria.

Os 12 meses de dados fictícios têm 10 tipos de anomalias plantadas de propósito (entrega atrasada, fatura duplicada, divergência de preço etc.), para que o painel e o copiloto tenham o que encontrar.

Telas por perfil (solicitante, aprovador, comprador, financeiro e gestor, escolhidos em "Entrar como"), painel do gestor com gráficos, exportação para Excel, modo técnico (mostra o SQL por trás de cada gráfico) e um copiloto que responde perguntas em linguagem natural com consultas SQL somente leitura.

## Stack

- **Banco:** PostgreSQL 17, migrações com Flyway (`db/init`, V1 a V11).
- **API:** Java 21, Spring Boot, Maven, JdbcClient com SQL explícito (sem JPA), Swagger (springdoc). Testes com Testcontainers.
- **Frontend:** React, TypeScript, Vite, Recharts.
- **Copiloto:** Ollama (local) ou Gemini (hospedado), configurável.
- **Dados:** gerados em Python com seed fixa (reproduzíveis).
- **Infra local:** Docker Compose.

## Como rodar localmente

Requisitos: Docker, Java 21, Node.js, Python 3 (só para regenerar os dados) e, opcionalmente, Ollama ou uma chave do Gemini para o copiloto. O Maven Wrapper (incluído no repositório) cuida do Maven — não precisa instalar.

1. **Variáveis (opcional):** copie `.env.example` para `.env` para mudar portas ou senhas de desenvolvimento (por exemplo `DB_PORT`, se a 5432 estiver ocupada).
2. **Banco:**
   ```powershell
   docker compose up -d
   docker compose ps -a   # espere o serviço flyway ficar Exited (0)
   ```
3. **Dados fictícios:** carregue os quatro arquivos de `db/seed`, nessa ordem (o usuário do banco é `metalcor`, definido por `DB_USER` no `docker-compose.yml`; não existe o usuário `postgres`):
   ```powershell
   docker cp db/seed/01_master_data.sql metalcor-db:/01_master_data.sql
   docker cp db/seed/02_purchasing.sql metalcor-db:/02_purchasing.sql
   docker cp db/seed/03_receipts.sql metalcor-db:/03_receipts.sql
   docker cp db/seed/04_invoices_payments.sql metalcor-db:/04_invoices_payments.sql

   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /01_master_data.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /02_purchasing.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /03_receipts.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /04_invoices_payments.sql
   ```
4. **API** (pasta `api/`):
   ```powershell
   $env:DB_PORT = "5432"
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"   # Windows
   ./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"       # Linux/macOS
   ```
   Swagger em http://localhost:8080/swagger-ui.html. Variáveis e detalhes em [`api/README.md`](api/README.md).

   > **Windows com espaço no nome de usuário:** se o Maven Wrapper (`.\mvnw.cmd`) falhar com `'C:\Users\nome' is not recognized as an internal or external command` e `Cannot start maven from wrapper`, defina antes `$env:MAVEN_USER_HOME = "D:\.mvnwrapper"` (qualquer caminho sem espaço). Detalhes em [`api/README.md`](api/README.md).
5. **Frontend** (pasta `web/`):
   ```powershell
   npm install
   npm run dev
   ```
   Abre em http://localhost:5173. Mais em [`web/README.md`](web/README.md).
6. **Testes da API:** `.\mvnw.cmd test` (Windows) ou `./mvnw test` (Linux/macOS) na pasta `api/` (precisam do Docker).

Para recriar o banco do zero: `docker compose down -v` e repita os passos 2 e 3.

## Decisões de arquitetura

- **Banco como fonte da verdade:** regras de integridade ficam em constraints (`CHECK`, chaves, únicas) e triggers; históricos (`approvals`, `audit_log`) são imutáveis por trigger.
- **Numeração de documentos por intervalos** (`number_ranges` e `next_document_number()`), sem sequences, para não deixar buracos.
- **Privilégio mínimo:** a API conecta como `metalcor_app` (sem DELETE) e o copiloto como `metalcor_readonly`; o dono do banco só aplica migrações.
- **Escritas transacionais** com auditoria: cada transação define `app.current_user_id`, usado pelos triggers de auditoria.
- **SQL explícito** (sem JPA e sem `SELECT *`), DTOs em records e dinheiro em `BigDecimal`.
- **Erros em ProblemDetail** (RFC 7807), sem stack trace nem detalhes do banco. As mensagens da API são em inglês e o frontend as traduz.
- **Copiloto seguro:** só uma instrução `SELECT`, só tabelas permitidas, limite de linhas e de tempo, usuário sem permissão de escrita e limite de perguntas por minuto.
- **Idiomas:** código e tabelas em inglês; interface em PT | EN; dados de exemplo em português.

## Limitações conhecidas

- **Autenticação provisória:** a API identifica o usuário pelo cabeçalho `X-User-Id`, sem senha, token ou sessão, e o frontend usa uma lista estática de usuários em "Entrar como". Serve para demonstrar perfis e auditoria; **não é segurança de produção**.
- Não é SAP e não cobre NF-e, campos fiscais, estoque nem multi-empresa.
- O copiloto depende de um modelo de linguagem e pode errar; as respostas devem ser conferidas contra o banco.
- A busca de documentos no frontend é por id (não há busca por número do documento).
- Sem testes automáticos de frontend. O deploy ainda não foi feito.

---

<a id="english"></a>

# English

A procure-to-pay mini-ERP built with Java, PostgreSQL and React. It is a study / portfolio project.

> **Disclaimer:** Metalcor Autopeças, every supplier, material, user and amount are **100% fictional**, created for portfolio purposes. Prices are estimates and any resemblance to real companies is coincidental. This project is **not SAP** and must not be presented as such: it is inspired by how ERPs organize the purchasing process.

## Overview

It simulates the purchasing cycle of a fictional auto-parts metalworking company:

**requisition → approval (value-based rule) → purchase order → goods receipt → invoice → payment**

The core is the **three-way match**: each invoice is checked against its purchase order and goods receipt using configurable tolerances (default 2% price, 5% quantity). Out-of-tolerance invoices are blocked until resolved. It draws on concepts from ERP purchasing and finance modules: header/item documents, number ranges, approvals and an audit trail.

Twelve months of fictional data include 10 deliberately planted anomaly types (late delivery, duplicate invoice, price mismatch, etc.). The app has role-based screens (requester, approver, buyer, finance, manager, chosen via "Sign in as"), a manager dashboard, Excel export, a technical mode that shows the SQL behind every chart, and a copilot that answers natural-language questions with read-only SQL.

## Stack

PostgreSQL 17 + Flyway (V1 to V11) · Java 21, Spring Boot, Maven, JdbcClient with explicit SQL (no JPA) · React + TypeScript + Vite · Ollama (local) or Gemini (hosted) for the copilot · Python for the seeded fictional data · Docker Compose.

## Running locally

Requirements: Docker, Java 21, Node.js, and optionally Ollama or a Gemini API key for the copilot. The included Maven Wrapper handles Maven — no separate install needed.

1. Optionally copy `.env.example` to `.env` to change ports or development passwords.
2. `docker compose up -d`, then wait for the `flyway` service to be `Exited (0)` (`docker compose ps -a`).
3. Load the four files in `db/seed`, in order (the database user is `metalcor`, set by `DB_USER` in `docker-compose.yml`; there is no `postgres` user):
   ```powershell
   docker cp db/seed/01_master_data.sql metalcor-db:/01_master_data.sql
   docker cp db/seed/02_purchasing.sql metalcor-db:/02_purchasing.sql
   docker cp db/seed/03_receipts.sql metalcor-db:/03_receipts.sql
   docker cp db/seed/04_invoices_payments.sql metalcor-db:/04_invoices_payments.sql

   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /01_master_data.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /02_purchasing.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /03_receipts.sql
   docker exec -it metalcor-db psql -U metalcor -d metalcor -f /04_invoices_payments.sql
   ```
4. API (in `api/`): `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` (Windows) or `./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"` (Linux/macOS). Swagger at http://localhost:8080/swagger-ui.html. See [`api/README.md`](api/README.md) for all environment variables.
   > **Windows with a space in the username:** if the Maven Wrapper (`.\mvnw.cmd`) fails with `'C:\Users\name' is not recognized as an internal or external command` and `Cannot start maven from wrapper`, first set `$env:MAVEN_USER_HOME = "D:\.mvnwrapper"` (any path without a space). Details in [`api/README.md`](api/README.md).
5. Frontend (in `web/`): `npm install` then `npm run dev`, at http://localhost:5173.
6. API tests: `.\mvnw.cmd test` (Windows) or `./mvnw test` (Linux/macOS) in `api/` (requires Docker).

## Key decisions

- The database is the source of truth: constraints, triggers, immutable history tables (`approvals`, `audit_log`).
- Document numbers come from number ranges, not sequences, so there are no gaps.
- Least privilege: the API connects as `metalcor_app` (no DELETE) and the copilot as `metalcor_readonly`; the owner role only runs migrations.
- Every write runs in one transaction and sets `app.current_user_id` for the audit triggers.
- Explicit SQL, records as DTOs, `BigDecimal` for money, errors as RFC 7807 `ProblemDetail` with no internals leaked. API messages are in English; the frontend translates them (PT | EN).
- Copilot guardrails: single `SELECT`, allow-listed tables, row and time limits, a read-only database role and a per-minute rate limit.

## Known limitations

- **Provisional authentication:** the API identifies the user through an `X-User-Id` header, with no password, token or session, and the front end uses a static user list. It demonstrates roles and auditing; it is **not production security**.
- Not SAP; no e-invoicing, tax fields, inventory or multi-company support.
- The copilot relies on a language model and can be wrong; check answers against the database.
- Document lookup in the UI is by id only. There are no front-end automated tests, and nothing is deployed yet.
