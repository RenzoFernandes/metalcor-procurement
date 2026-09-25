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

O frontend não tem backend próprio: consome a API Java, que **precisa estar rodando em paralelo** (por padrão em `http://localhost:8080/api/v1`). Para usar outro endereço, defina `VITE_API_BASE_URL` (veja `.env.example`).

## Estado atual (v0.6a)

Esqueleto: tela "Entrar como", layout, seletor PT | EN e tela inicial. O cliente HTTP (`src/api/client.ts`) já cobre os endpoints existentes, mas ainda não é usado pelas telas.

Limitação temporária: a lista de usuários da tela "Entrar como" é estática (cópia do seed), pois a API ainda não tem endpoint de usuários.