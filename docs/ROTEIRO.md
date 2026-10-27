# Roteiro de desenvolvimento

Uma fase por sessão (ou mais, se for grande). Cada fase termina com um APK instalável que o dono testa nos aparelhos. Ao concluir uma fase:
1. Rode os testes possíveis no seu ambiente.
2. Atualize "Estado atual" no fim deste arquivo.
3. Abra um pull request com um resumo em português simples: o que mudou, o que testar em cada aparelho e o que ficou pendente.

---

## Fase 0 — Fundação
- Projeto Gradle (Kotlin DSL) com `:core` (JVM) e `:app` (Android), version catalog e wrapper commitado.
- Assinatura, `versionCode`/`versionName` e ABI conforme `CLAUDE.md`.
- Ícone: copie `branding/android/*` para `app/src/main/res/`. Fontes Anton e Atkinson Hyperlegible em `res/font` (e a licença OFL em `assets/licenses/`).
- Temas Compose para `TABLET`, `EINK_COLOR` e `EINK_MONO`. Em e-ink, desligue ripple e animações no tema.
- `DeviceProfile` com detecção, diálogo de confirmação no primeiro uso em Onyx, escolha manual em Ajustes e tela Diagnóstico.
- `EinkController` com implementação genérica (a da Onyx entra na Fase 2).
- Tela da biblioteca vazia com logo, wordmark e chamada "Importe seu primeiro quadrinho" (botão ainda sem ação), navegação inferior Biblioteca / Compartilhar / Ajustes.
- **Chave de assinatura** (faça uma vez, sem commitar nada):
  1. Gere uma senha forte: `openssl rand -base64 24 | tr -d '/+='`.
  2. Gere o keystore PKCS12 com alias `gibiflix`, chave e keystore com a mesma senha. Use `keytool -genkeypair -storetype PKCS12 -keystore gibiflix.keystore -alias gibiflix -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Gibiflix" -storepass <SENHA> -keypass <SENHA>`; se não houver `keytool`, use `openssl req -x509 -newkey rsa:4096 -sha256 -days 36500 -nodes -subj "/CN=Gibiflix" -keyout k.pem -out c.pem` seguido de `openssl pkcs12 -export -inkey k.pem -in c.pem -name gibiflix -out gibiflix.keystore -passout pass:<SENHA>`.
  3. Mostre ao dono, cada um num bloco de código separado: o conteúdo de `base64 -w0 gibiflix.keystore` (secret `GIBIFLIX_KEYSTORE_BASE64`) e a senha (secret `GIBIFLIX_KEYSTORE_PASSWORD`). Explique que ele deve cadastrar os dois em Settings → Secrets and variables → Actions e guardar uma cópia num lugar seguro.
  4. Apague `gibiflix.keystore`, `k.pem` e `c.pem` do ambiente.

**Aceite**: o CI na `main` fica verde e publica um Release com o APK; o app instala no tablet e nos dois Boox; o Diagnóstico mostra o perfil certo (ou o diálogo permite corrigir); o ícone aparece certo, inclusive no ícone temático do Android 13+; `:core:test` roda (pode ter um teste simples por enquanto).

## Fase 1 — Importação e biblioteca
- Importação de CBZ/ZIP, CBR/RAR (incluindo RAR5), CB7/7Z e PDF por seletor de arquivos, pasta e "Abrir com/Compartilhar".
- Conversão para o pacote (`docs/FORMATO_GIBIFLIX.md`), ainda sem quadros (`panels` vazios), com hash, capa e ComicInfo/heurística de nome.
- Room: quadrinhos, séries, coleções. Abas Lendo, Todos, Séries, Coleções; busca; ordenação; editar informações; excluir; status Novo.
- Importação em WorkManager com progresso.

**Aceite**: importar um de cada formato funciona nos três aparelhos; duplicado é recusado com aviso; séries agrupam pelo ComicInfo; testes do `:core` para ComicInfo e heurística de nomes.

## Fase 2 — Leitor (modo página), botões e imagem
- Leitor em modo página: trim (quando existir), páginas duplas em metades no retrato, zonas de toque, menu central, barra de páginas.
- Botões de volume e teclas (§7 da especificação), incluindo segurar para mudar de página e as opções de trocar/desligar.
- Filtros de imagem por perfil no `:core` com testes, tela de Ajustes de imagem com prévia ao vivo.
- `EinkController` da Onyx (SDK `onyxsdk-device`) com queda para o genérico; refresh completo a cada N trocas; limpeza por flash.
- Progresso salvo; "Continuar lendo"; status Lido.
- Cache de páginas em disco e pré-carregamento.

**Aceite**: no Boox colorido e no preto e branco, Volume + / Volume − (e os botões físicos de página, se existirem) passam as páginas sem mudar o volume; o refresh completo acontece no intervalo configurado; os filtros mudam a prévia; o progresso volta ao reabrir.

## Fase 3 — Preparação e leitura quadro a quadro
- Algoritmo de detecção do §5 no `:core`, com todos os testes listados lá.
- Pipeline de preparação (§4): páginas duplas, quadros, trim, confiança, gravação atômica do `gibiflix.json`, status Preparando/Revisar/Pronto. "Preparar ao importar" conforme o perfil.
- Leitor quadro a quadro: ajuste do quadro à tela, margem, ampliação máxima, divisão de quadros muito largos, página inteira antes/depois (opcional), toque duplo alterna modos, transição animada só no tablet, indicadores (miniatura com destaque, página X/Y, quadro A/B).

**Aceite**: num quadrinho ocidental típico com sarjetas brancas, a maioria das páginas sai correta sem revisão; o mangá marcado como `rtl` segue a ordem certa; no Boox a troca de quadro é instantânea e legível.

## Fase 4 — Editor de quadros
- Tela de Preparação do §6: miniaturas com estado, sobreposição numerada, selecionar/mover/redimensionar, Desenhar, Juntar, Dividir, Apagar, reordenar arrastando, direção por livro e por página, desfazer/refazer, redetectar com tolerância, Aprovar e ir para a próxima, filtro "só as que precisam de revisão".
- Layout de uma coluna para telas pequenas.

**Aceite**: dá para corrigir uma página errada inteira só com toques no tablet; `revision` sobe; o leitor reflete as mudanças na hora.

## Fase 5 — Compartilhar entre aparelhos
- Rede local (§9): servidor Ktor + NSD, pareamento por código, lista Novos/Atualizados/Já tenho, download com progresso, sincronização de progresso e marcadores nos dois sentidos.
- Exportar para pasta e importar de pasta com `gibiflix-sync.json`.
- Botão **Enviar para o Boox** do editor abre o Compartilhar já no modo de envio.

**Aceite**: preparar no tablet, receber no Boox Go 7 Color e no Go 7 pela Wi-Fi; ler um pouco num aparelho e continuar no outro após sincronizar; testes do `:core` para a fusão de progresso.

## Fase 6 — Servidores OPDS
- Cadastro de servidores com senha criptografada, navegação, busca, capas, download → importar → preparar (§10).

**Aceite**: navegar e baixar de um Komga ou Kavita real do dono; parser OPDS testado no `:core` com feeds de exemplo escritos no teste.

## Fase 7 — Marcadores, estatísticas e backup
- Marcadores com nota (§11), lista por quadrinho e geral.
- Sessões de leitura e tela de Estatísticas.
- Backup leve e completo, restauração com fusão, lembrete semanal opcional.

**Aceite**: restaurar um backup leve num aparelho limpo traz progresso, coleções e marcadores de volta.

## Fase 8 — Texto dos balões
- OCR na preparação (§12), gravação no pacote, busca nos balões, botão **Ler texto** no leitor.
- Tradução no aparelho ou gravada pelo tablet, conforme o teste nos Boox.

**Aceite**: buscar uma fala encontra o quadrinho e abre no quadro certo; "Ler texto" mostra o texto do quadro em letra grande no Boox.

---

## Estado atual
- Fase concluída mais recente: **Fase 8** (código de todas as fases 0–8 escrito, `:core:test` passando e o CI compilando o APK). Os critérios de aceite que dependem dos aparelhos (instalar nos três, botões do Boox, refresh, Wi-Fi entre aparelhos, Komga/Kavita real, OCR nos Boox) ainda precisam do teste do dono.
- Modelos de aparelho confirmados (`Build.MODEL`): ainda não informados. Mande o texto de Ajustes → Diagnóstico → Copiar de cada Boox para completar `KNOWN_COLOR_MODELS` (`core/.../device/Profile.kt`).
- Pendências e decisões tomadas:
  - Versões: AGP 8.13.2, Kotlin 2.3.21, Gradle 8.14.3, compileSdk/targetSdk 36. `libarchive` fica na 1.1.6 porque a 1.1.7 exige compileSdk 37 (AGP 9).
  - O `:app` só entra no build quando há Android SDK (`settings.gradle.kts`), para `./gradlew :core:test` rodar em qualquer JDK.
  - SDK da Onyx (`onyxsdk-device` 1.3.5.3, repositório `repo.boox.com`) chamado só por reflexão: se faltar ou falhar, o app usa o controle genérico (flash preto). Confirmar no Boox se o Diagnóstico mostra "Controle e-ink: Onyx (SDK)".
  - Detecção de quadros: além do §5, há um "corte macio" (faixas com ≥ 30% de sarjeta) para separar quadros ligados por balão que atravessa a sarjeta, e colunas com sarjetas alinhadas são lidas linha por linha. Em página dupla, procura primeiro a sarjeta central.
  - Página sem quadros detectados (sangrada) fica com `panels` vazio e confiança 0,8.
  - Tradução dos balões: feita na preparação (ML Kit Translation) e gravada em `translations`, escolhida em Ajustes → Preparação → "Traduzir os balões para". Falta testar se o download do modelo funciona nos Boox sem Play Services; se não funcionar, preparar no tablet.
  - Backup leve guarda as senhas dos servidores só se o dono marcar a opção (ficam legíveis no arquivo).
  - Chave de assinatura gerada na Fase 0 e entregue ao dono para cadastrar nos Secrets. Sem os Secrets, o APK sai com chave de debug.
