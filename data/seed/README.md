# Seed data (dados de base)

Todos os fornecedores, empresas e valores deste diretório são **fictícios**.
Qualquer semelhança com empresas reais é coincidência.

## Arquivos
- `suppliers.csv`: 12 fornecedores, 2 por categoria (um `premium` e um `economy`).
- `materials.csv`: 31 materiais reais do ramo metalúrgico/autopeças, com unidade,
  faixa de preço de referência (`price_min` a `price_max`, em R$) e consumo mensal
  típico (`monthly_qty_min` a `monthly_qty_max`).

## Como o gerador usa esses dados
- **Preço:** o fornecedor `economy` pratica preços perto de `price_min` e o `premium`,
  perto de `price_max`, com pequena variação por pedido e ao longo do ano.
- **Prazo e pontualidade:** `lead_time_days` e `on_time_rate` alimentam as datas de
  entrega. As anomalias (atraso, entrega parcial, divergência de preço) aparecem com
  mais frequência nos fornecedores `economy`, como na vida real.
- **Categorias:** ACO (aço), ROL (rolamentos), FER (ferramentas de corte),
  TIN (tintas e químicos), EMB (embalagens), MAN (manutenção e EPI).

## Sobre a origem dos números
Os preços e os consumos são **estimativas** para dar realismo, e não cotações.
Os códigos de material (rolamento 6205, inserto CNMG 120408, aço SAE 1006 etc.)
seguem designações padrão do mercado. Foram conferidos por amostragem apenas o motor
elétrico de 5 cv e o rolamento 6205, em anúncios de varejo online.
