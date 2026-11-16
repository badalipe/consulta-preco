# Consulta Preço

Aplicativo Android para pesquisar produtos por nome ou código de barras, consultar preços, procurar imagens e guardar localmente os produtos já encontrados para acelerar pesquisas futuras.

## Funcionalidades

- Pesquisa por texto ou voz em português.
- Leitura de código de barras com sinal sonoro.
- Consulta de preço e dados do produto.
- Procura de imagem por código ou nome, com cache para pesquisas futuras.
- Desbloqueio local na primeira utilização.
- Reconexão automática do serviço de consulta.

As imagens dependem da disponibilidade do produto em catálogos externos e de uma ligação à internet. Se não for encontrada uma foto, é apresentada uma imagem de substituição. A cache de produtos começa vazia e é preenchida durante a utilização.

## Primeira utilização

Na primeira abertura após instalar, o app pede uma senha local. Depois da validação, o desbloqueio é guardado no aparelho e não é pedido novamente, exceto se os dados do app forem apagados ou o app for reinstalado.

## Compilar pelo GitHub Actions

O workflow **Actions → Build APK → Run workflow** compila o APK. Configure os segredos de acesso exigidos pelo workflow em **Settings → Secrets and variables → Actions**. O workflow valida que os segredos necessários existem sem imprimir os valores nos logs.

Depois de o workflow terminar com sucesso, abra a execução e descarregue o artefacto **apk-build**. O APK de instalação encontra-se dentro desse artefacto. No Android, autorize a instalação do ficheiro descarregado e abra o app.

## Segurança

As credenciais do serviço são incorporadas no APK de acordo com a configuração atual de compilação e podem ser extraídas por alguém que obtenha o ficheiro. Restrinja a distribuição do APK e rode as credenciais se o ficheiro for partilhado fora do grupo autorizado. A senha local de primeira abertura é incorporada como hash.

O app não tenta contornar CAPTCHA, bloqueios ou desafios de segurança. O Android também pode suspender verificações quando o app está em segundo plano; a sessão é verificada novamente quando o app regressa ao primeiro plano.

## Requisitos

Android 7.0 ou superior.
