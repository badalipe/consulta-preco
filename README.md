# Consulta Preço

Aplicativo Android para consultar produtos e preços do Cartaz Fácil. A tela foi refeita com base no estilo visual do app ComparaPreço, mas mostra apenas os dados de preço do Cartaz Fácil.

## O que o app faz

A pesquisa pode ser iniciada digitando o nome ou código, tocando no microfone para falar em português do Brasil ou lendo o código de barras com a câmera. O app envia a consulta ao Cartaz Fácil e mostra o nome do produto, o código e o preço encontrado. O conteúdo do site é carregado num WebView interno e não aparece na tela principal.

Na primeira abertura após instalar, o app pede uma senha de acesso local. Depois da validação, registra o desbloqueio no aparelho e não pergunta novamente, a menos que os dados do app sejam apagados ou que ele seja reinstalado.

## Sessão e reconexão

As credenciais do Cartaz Fácil são usadas para autenticar automaticamente. Enquanto o app está aberto, ele verifica a sessão a cada 10 segundos. Se outra sessão invalidar o acesso do app, o Consulta Preço tenta entrar de novo sem trocar para a página do site. Como a conta permite apenas uma sessão, essa reconexão pode encerrar a sessão que estiver aberta no computador.

O Android pode suspender verificações quando o app fica em segundo plano. Ao voltar para o app, a sessão é verificada imediatamente. O aplicativo não tenta contornar CAPTCHA, bloqueios do site ou desafios de segurança; se o Cartaz Fácil impedir o acesso, será necessário resolver isso com o suporte do serviço.

## Configurar e compilar pelo GitHub Actions

O workflow **Actions → Build APK → Run workflow** compila o APK. Antes de executá-lo, configure estes segredos no repositório em **Settings → Secrets and variables → Actions**: `CARTAZ_USERNAME`, `CARTAZ_PASSWORD` e `APP_FIRST_PASSWORD`. O workflow valida se os três existem e não imprime seus valores nos logs.

Após o workflow terminar com sucesso, abra a execução e baixe o artefato **apk-build**. O APK de instalação fica dentro desse artefato. No Android, permita a instalação do arquivo baixado e abra o aplicativo.

## Segurança

Os segredos não ficam escritos no código-fonte. Porém, como solicitado, o usuário e a senha do Cartaz Fácil são incorporados ao APK compilado para permitir o login automático e podem ser extraídos por alguém que obtenha o arquivo. Restrinja a distribuição do APK e troque os segredos se o arquivo for compartilhado fora do grupo autorizado. A senha local de primeira abertura é incorporada como hash, não em texto simples.

## Detalhes técnicos

O fluxo de busca usa os elementos `#btnBusca` e `#pesq_prod` do módulo Cartaz Unitário e lê o preço nas opções de `#select_valores`. O scanner usa a atividade Android existente e o reconhecimento de voz usa o serviço de fala disponível no aparelho. O projeto requer Android 7.0 ou superior.
