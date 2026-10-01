# Metalcor Procurement — web

Frontend (Vite + React + TypeScript) do mini-ERP de compras da Metalcor Autopeças. Projeto de estudo; empresas, nomes e dados são fictícios.

## Instalar e rodar

```powershell
cd web
npm install
npm run dev
```

O app abre em http://localhost:5173. Para gerar o build: `npm run build`.

## API

O frontend não tem backend próprio: consome a API Java, que **precisa estar rodando em paralelo** (por padrão em `http://localhost:8080/api/v1`). Para usar outro endereço, defina `VITE_API_BASE_URL` (veja `.env.example`). A API precisa liberar a origem do frontend em `APP_CORS_ALLOWED_ORIGIN`.

## O que já existe

- **Entrar como:** escolha de perfil (solicitante, aprovador, comprador, financeiro, gestor), sem cadastro. O usuário escolhido vai no cabeçalho `X-User-Id` das escritas.
- **Fluxo procure-to-pay:** criar e enviar requisição, aprovar ou rejeitar, emitir pedido, registrar recebimento e fatura (com resultado do three-way match), aprovar e pagar a fatura. Cada documento tem tela de detalhe, aberta por id (a API ainda não busca por número do documento).
- **Painel do gestor:** KPIs e gráficos (gasto por mês, exceções, pagamentos atrasados, faturas paradas, scorecard de fornecedores) e exportação para Excel.
- **Copiloto:** painel de perguntas em linguagem natural, que mostra a resposta em tabela.
- **Modo técnico:** mostra o SQL de gráficos e respostas do copiloto.
- **Idiomas:** seletor PT | EN (português por padrão), com textos em `src/i18n/pt.json` e `en.json`. Os erros da API são traduzidos pelo frontend.

## Limitações

- A lista de usuários da tela "Entrar como" é estática (cópia do seed), pois a API não tem endpoint de usuários.
- A identificação é provisória (`X-User-Id`, sem senha nem token): serve para demonstração, não para produção.
- Sem testes automáticos de frontend por enquanto.
