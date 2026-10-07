# Consulta de Preço — App Android (ML Kit + Auto-login + Auto-reconnect)

App de consulta rápida de preços para o sistema Cartaz Fácil
(https://rmarket.cartazfacil.pro/unitario.php).

## Como funciona (fluxo do consultor de preço)

```
1. Toca no link (login automático, uma vez só)
        ↓
2. CÂMERA aberta (ML Kit) — apontou, leu
        ↓
3. App busca no Cartaz Fácil em segundo plano (WebView invisível)
        ↓
4. TELA VERDE: NOME do produto + PREÇO GIGANTE + bipe
        ↓
5. Toque em qualquer lugar → escaneia outro (ou volta sozinho em 20s)
```

- Um único botão na tela de resultado: "ESCANEAR OUTRO PRODUTO"
- WebView fica 100% invisível — o usuário nunca vê o sistema, só nome + preço
- Se a sessão cair/duplo login → reconecta sozinho (forcar=true)

## Como funciona (detalhes técnicos)

1. **Abertura via link (deep link)** — o app é aberto por um link que já traz
   usuário e senha. Exemplo:

   ```
   consultapreco://abrir?url=https://rmarket.cartazfacil.pro/unitario.php&usuario=LOJA01&senha=1234&forcar=true
   ```

   Parâmetros:
   | Parâmetro | Obrigatório | Descrição |
   |-----------|-------------|-----------|
   | `usuario` | sim | login do sistema |
   | `senha` | sim | senha do sistema |
   | `url` | não | página de consulta (padrão: unitario.php) |
   | `forcar` | não | `true` derruba sessão dupla e reconecta (padrão: true) |

2. **Scanner ML Kit** — apontou a câmera, leu o código de barras (EAN-13, EAN-8,
   UPC, Code 128/39), o app injeta o código no campo de busca da página e o preço aparece.

3. **Reconexão automática** — um watchdog testa a sessão a cada 15 segundos.
   Se a sessão cair (queda de rede, logout, **duplo login**), o app reconecta
   sozinho com backoff progressivo (até 30s). Com `forcar=true`, limpa os
   cookies antes de logar de novo, assumindo a sessão.


## 📱 COMPILAR PELO CELULAR (sem PC) — GitHub Actions

1. Crie uma conta gratuita em https://github.com
2. Crie um repositório novo (botão "New" → nome: consulta-preco → Public)
3. No repositório, clique em "uploading an existing file" e envie TODOS
   os arquivos e pastas deste projeto (dá pra selecionar vários de uma vez;
   a pasta .github/workflows/build-apk.yml é essencial)
4. Vá em **Actions** → **Build APK** → **Run workflow**
5. Aguarde ~5 minutos (bolinha amarela → verde ✅)
6. Clique no workflow concluído → **consulta-preco-apk** (em Artifacts) → baixe
7. No Android, abra o APK baixado e instale (permita "instalar apps desconhecidas")
8. Pronto! Toque no link deep link para logar e usar

## Alternativa: compilar no PC
Instale o Android Studio, abra esta pasta, conecte o celular e rode.

## Como abrir o projeto (PC)

1. Instale o **Android Studio** (versão Hedgehog ou superior).
2. *File → Open* e selecione esta pasta.
3. Aguarde o Gradle sincronizar e rode em um celular Android (min. Android 7.0).

## ✅ SELETORES CONFIRMADOS (extraídos do HTML real em 2026-10-07)

- Campo de busda: `#btnBusca` | Botão: `#pesq_prod` (clique disparado via jQuery)
- Sessão válida = campo `#btnBusca` presente na página
- Se cair na home após login, o app clica sozinho no link "Cartaz Unitario" do menu
- Login automático usa seletores genéricos de formulário; se a tela de login do
  Cartaz Fácil tiver nomes diferentes, cole o código-fonte da página de login
  (abrir o site deslogado → Ctrl+U / view-source:) para refinar.

## OBSOLETO — pontos antigos de ajuste

Como a página exige a rede/sessão de vocês, os seletores do HTML estão com
valores de exemplo. Ajuste no arquivo `SistemaActivity.kt`:

```kotlin
private const val SEL_USER  = "input[name='login']"     // campo usuário do login
private const val SEL_PASS  = "input[name='senha']"     // campo senha do login
private const val SEL_BTN   = "input[type='submit']"    // botão entrar
private const val SEL_BUSCA = "input[name='codigo']"    // campo de código de barras
private const val MARCA_SESSAO = "unitario"             // texto que só aparece logado
```

**Para descobrir os nomes certos:** abra `unitario.php` no Chrome do PC,
aperte F12 → aba *Elements*, clique no campo e veja o atributo `name`/`id`.

## ⚠️ Segurança do link

O link contém a senha em texto puro — quem tiver o link, loga no sistema.
Recomendado: gerar um link por balcão/operador e, se possível, pedir ao
suporte do Cartaz Fácil um **token de sessão temporário** no lugar da senha.
