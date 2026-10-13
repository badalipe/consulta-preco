# Agente de Dados - Cartaz Fácil

## Objetivo
Analisar formatos de dados e estruturas de resposta

## Formato de Resposta (buscaProduto.php)
- Tabela HTML com ID do produto
- Select com preços
- Descrição em small.descRetornoProd

## Campos Identificados
| Campo | Seletor | Tipo |
|-------|---------|------|
| ID Produto | tr[id^="trValor"] | number |
| Código | .clsTrValor b | string |
| Descrição | .descRetornoProd* | string |
| Preço | #select_valores option | float |
