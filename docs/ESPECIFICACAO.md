# Gibiflix — especificação

## 1. Visão geral
Gibiflix é uma biblioteca pessoal de quadrinhos com leitura guiada quadro a quadro. O fluxo principal:

1. **Importar** um quadrinho (CBZ, CBR, CB7 ou PDF) de arquivos, pasta, outro aparelho ou servidor OPDS.
2. **Preparar**: converter para o pacote Gibiflix (`.cbz` + `gibiflix.json`), detectar margens, páginas duplas e quadros, e marcar páginas duvidosas para revisão. Normalmente feito no Galaxy Tab S11, mas funciona em qualquer aparelho.
3. **Revisar** (opcional) no editor de quadros.
4. **Enviar** o pacote pronto para os Boox pela rede local ou por pasta.
5. **Ler** quadro a quadro ou página a página, com botões de volume, imagem otimizada para cada tela e progresso sincronizado.

Aparelhos-alvo: Galaxy Tab S11 (tablet), Boox Go 7 Color (e-ink colorido) e Boox Go 7 (e-ink preto e branco). Deve funcionar em qualquer Android 11+.

## 2. Perfis de dispositivo
Três perfis: `TABLET`, `EINK_COLOR`, `EINK_MONO`.

### Detecção
1. Leia `Build.MANUFACTURER`, `Build.BRAND`, `Build.MODEL`, `Build.DEVICE`, `Build.PRODUCT`.
2. Se fabricante ou marca contiver "onyx" ou "boox" (sem diferenciar maiúsculas), é e-ink. Caso contrário, `TABLET`.
3. Sendo Onyx: é colorido se modelo/produto contiver "color" ou estiver na lista `KNOWN_COLOR_MODELS`; senão, preto e branco. Os nomes exatos de modelo não são conhecidos de antemão, então:
4. Na primeira execução num Onyx, mostre um diálogo: "Encontramos um Boox. A tela dele é colorida ou preto e branco?", com a resposta da heurística pré-selecionada. Guarde a escolha.
5. Em Ajustes: "Perfil do aparelho: Automático / Tablet / E-ink colorido / E-ink preto e branco".
6. Tela **Diagnóstico** (Ajustes → Diagnóstico): mostra os campos de `Build`, resolução em px, densidade, perfil detectado e perfil em uso, com botão "Copiar". Serve para o dono mandar os dados e você completar `KNOWN_COLOR_MODELS`.

### O que muda por perfil

| | TABLET | EINK_COLOR | EINK_MONO |
|---|---|---|---|
| Tema | escuro, fundo preto puro | claro, papel branco, acento vermelho | claro, papel branco, só preto |
| Animações | sim (transição suave entre quadros) | não | não |
| Filtro de imagem padrão | nenhum | contraste +20, saturação +35, nitidez média | escala de cinza, contraste +25, meios-tons mais escuros, nitidez média |
| Refresh completo | — | a cada 6 trocas | a cada 10 trocas |
| Dividir quadros muito largos | não | sim | sim |
| Preparar ao importar | sim | não (pergunta) | não (pergunta) |
| Cache de páginas | 400 MB | 200 MB | 200 MB |

Todos os valores são padrões ajustáveis em Ajustes, guardados por perfil.

### Filtros de imagem (no `:core`, sobre `IntArray` ARGB já redimensionado para a tela)
- Contraste e brilho (curva linear em torno de 128).
- Saturação (matriz de cor; ignorada no mono).
- Meios-tons: curva gama aplicada à luminância ("Escurecer meios-tons").
- Nitidez: unsharp mask 3×3, intensidades Desligada/Leve/Média/Forte.
- Escala de cinza (mono): luminância Rec. 709.
- Pontilhado (só mono, padrão desligado): Floyd–Steinberg para 16 níveis de cinza.
- Ordem: redimensionar → cor/contraste/meios-tons → cinza (mono) → nitidez → pontilhado.
- Ajustes tem uma prévia ao vivo com um quadro de exemplo da biblioteca (ou gerado, se a biblioteca estiver vazia).

### Refresh e-ink
- Interface `EinkController` com `onContentChanged()`, `fullRefresh()`, `setReadingMode(on)`.
- Implementação Onyx usa o SDK `onyxsdk-device` (veja exemplos em `github.com/onyx-intl/OnyxAndroidDemo`, que também indica o repositório Maven e a versão). Exemplo de uso esperado: modo de atualização rápido durante a leitura e `EpdController.invalidate(view, UpdateMode.GC)` (ou equivalente) para o refresh completo.
- Implementação genérica (qualquer e-ink sem SDK): o "refresh por flash" desenha um quadro todo preto por ~60 ms e depois a imagem. Ajustável em Ajustes ("Limpeza por flash").
- Se o SDK da Onyx não carregar, caia na genérica sem travar o app.

## 3. Formatos e importação
- Entradas: `.cbz`, `.zip` (de imagens), `.cbr`, `.rar`, `.cb7`, `.7z`, `.pdf`. Imagens: JPEG, PNG, WebP (e AVIF se o Android decodificar).
- Origens: seletor de arquivos (vários de uma vez), importar pasta inteira (recursivo), "Abrir com"/"Compartilhar" de outros apps (intent filters para esses tipos), outro aparelho Gibiflix (§9), servidor OPDS (§10).
- Duplicados: hash SHA-256 do arquivo original. Se já existir, avise e não importe de novo.
- Tudo vira o **pacote Gibiflix** (`docs/FORMATO_GIBIFLIX.md`):
  - CBZ: copia as páginas (ordem natural de nome: "2" antes de "10"), ignora arquivos que não são imagem e pastas de sistema (`__MACOSX`, `.DS_Store`, `Thumbs.db`).
  - CBR/CB7: extrai e reempacota.
  - PDF: renderiza cada página com o lado maior = 2560 px (ajustável), JPEG qualidade 90.
  - `ComicInfo.xml` do original é preservado.
- Armazenamento: `filesDir/library/<id>.cbz`, capas em `filesDir/covers/<id>.webp`. O arquivo original não é guardado.
- Metadados:
  - Do `ComicInfo.xml`: Series, Number, Volume, Title, Writer, Penciller, Summary, Year, LanguageISO e Manga. `Manga = YesAndRightToLeft` define leitura da direita para a esquerda.
  - Sem ComicInfo: heurística no nome do arquivo, por exemplo "Série #12 (2020)", "Série v02", "Série 015", "Série - Título".
  - O dono pode editar título, série, número e direção de leitura.

## 4. Preparação
Roda em WorkManager (notificação com progresso, retoma se o app fechar). Status visíveis na biblioteca: **Novo**, **Preparando N%**, **Revisar** (há páginas com baixa confiança), **Pronto**, **Lido**.

Por página:
1. **Página dupla**: largura/altura > 1,2 → `spread = true`.
2. **Quadros**: algoritmo do §5, sobre uma cópia da página em cinza com o lado maior ≤ 1200 px.
3. **Margens (trim)**: união dos quadros + 1% de folga, limitada à página. Só aplica se cortar ≥ 1% de algum lado. Serve ao modo página.
4. **Confiança** e flag `reviewed = false` (o editor muda para `true`).
5. **OCR** (Fase 8, opcional): texto dos balões por quadro.

Ao fim, grava `gibiflix.json` no pacote (escrita atômica) e atualiza o banco. Repreparar um quadrinho mantém as páginas já revisadas, a menos que o dono peça "Refazer tudo".

## 5. Algoritmo de detecção de quadros (`:core`)
Entrada: `GrayImage(width, height, pixels: ByteArray)`, direção de leitura e parâmetros. Saída: lista de retângulos normalizados em ordem de leitura, confiança 0..1 e dados de depuração.

1. **Fundo (sarjeta)**: mediana de uma faixa de 2% em volta da borda. Se a variação dessa faixa for alta (página sangrada, sem margem), devolva um único quadro = página inteira, com confiança 0,8.
2. **Candidatos a sarjeta**: pixels com |p − fundo| ≤ tolerância (padrão 40). Funciona para sarjeta branca ou preta.
3. **Sarjeta real**: flood fill a partir de todos os pixels da borda, andando só em candidatos. Áreas brancas internas (balões, céu) não ligadas à borda não contam como sarjeta.
4. **Corte XY recursivo** na área de conteúdo: numa região, procure faixas de linhas inteiras (≥ 98% sarjeta) com espessura ≥ 0,4% da altura. Se existirem e separarem partes não triviais, corte na horizontal. Senão, tente o mesmo nas colunas e corte na vertical. Recursão até não haver cortes. Cada folha vira um candidato, apertado ao retângulo envolvente do conteúdo dentro dela.
5. **Folhas irregulares**: se o conteúdo ocupa < 75% do retângulo da folha, ou a folha tem mais de um componente conexo grande, rode componentes conexos (não sarjeta) dentro dela. Cada componente com área ≥ 1,5% da página vira um quadro. Componentes pequenos (balões que atravessam a sarjeta, legendas, números de página) se juntam ao quadro que mais sobrepõem; se não sobrepõem nenhum, são descartados.
6. **Limpeza**: descarte retângulos < 1,5% da página (se sobrar algum outro), funda pares com IoU > 0,6 ou contenção > 90%.
7. **Ordem**: a árvore do corte XY já dá a ordem: cortes horizontais de cima para baixo, verticais da esquerda para a direita (`ltr`) ou da direita para a esquerda (`rtl`). Dentro de folhas resolvidas por componentes: agrupe em linhas (sobreposição vertical > 50%), linhas de cima para baixo, quadros pela direção.
8. **Confiança**: começa em 1,0. Desconte 0,2 se o passo 5 foi usado, 0,2 se há sobreposição entre quadros, 0,3 se os quadros cobrem < 70% da área de conteúdo, 0,2 se houver > 12 quadros. Abaixo de 0,7, a página é marcada para revisão.

Testes obrigatórios (imagens sintéticas geradas no teste): grade 2×3 com sarjeta branca; mesma grade com sarjeta preta; quadro grande à esquerda ocupando duas linhas + dois à direita (ordem esperada: esquerda, direita-cima, direita-baixo); balão atravessando a sarjeta (não pode fundir os quadros); página sangrada; ordem `rtl` de mangá; página com ruído/número no rodapé; página dupla.

## 6. Editor de quadros (tela "Preparação")
Layout no tablet (paisagem):
- Barra superior: voltar à biblioteca, título "Preparando · <título>", estado ("Detecção automática concluída" / "Detectando N de M"), botão principal **Enviar para o Boox**.
- Coluna esquerda: miniaturas das páginas, com estado (revisada, verificar, pendente). Filtro "Só as que precisam de revisão".
- Centro: a página com os quadros contornados em azul tracejado e numerados na ordem de leitura. O quadro selecionado fica em laranja, com alças nos cantos.
- Coluna direita: "Página N de M", "K quadros detectados · confiança alta/média/baixa", direção de leitura (Ocidental → / Mangá ←, por livro, com exceção por página), ferramentas **Desenhar**, **Juntar**, **Dividir**, **Apagar**, lista da ordem de leitura (arrastar para reordenar), progresso "X de M páginas revisadas" e **Aprovar e ir para a próxima**.

Interações: tocar seleciona; arrastar alças redimensiona; arrastar o centro move; Desenhar = arrastar na página cria um quadro; Juntar = selecione dois ou mais e toque em Juntar; Dividir = trace uma linha sobre o quadro selecionado; Apagar remove; desfazer/refazer (20 passos); pinça para dar zoom na página. "Redetectar esta página" com tolerância ajustável. Salva sozinho (incrementa `revision`).

No celular ou no Boox o editor também abre, em layout de uma coluna com as ferramentas numa barra inferior.

## 7. Leitor
### Modos
- **Quadro a quadro** (padrão quando há quadros): mostra só o quadro, centralizado e ampliado para caber na tela, com 2% de margem (ajustável de 0 a 5%) e ampliação máxima de 3× (ajustável). Fundo branco no e-ink e preto no tablet.
- **Página inteira**: página aparada (trim) ajustada à tela. Página dupla numa tela em retrato é mostrada em metades, na ordem da direção de leitura.
- Toque duplo alterna os modos, mantendo a posição.
- Opções: "Mostrar a página inteira antes dos quadros" e "Mostrar a página inteira depois dos quadros" (ambas desligadas por padrão).
- **Quadros muito largos ou altos**: se o quadro ajustado ocupar menos de 35% da dimensão correspondente da tela, divida-o em 2 ou 3 vistas com 10% de sobreposição, percorridas como subpassos.

### Navegação
- Próximo: Volume +, tocar no terço direito, deslizar para a esquerda (só tablet), teclas `PAGE_DOWN`, `DPAD_RIGHT`, `DPAD_DOWN`, `SPACE`.
- Anterior: Volume −, terço esquerdo, deslizar para a direita (só tablet), `PAGE_UP`, `DPAD_LEFT`, `DPAD_UP`.
- Segurar Volume + / Volume − (≥ 600 ms): próxima / página anterior.
- Toque no centro: menu (título, barra de páginas com miniatura, modo, direção, marcador, brilho do filtro, sair).
- Em livros `rtl`, as zonas de toque se invertem; os botões de volume não (Volume + é sempre "próximo"). Ajustes: "Trocar Volume + e Volume −", "Usar botões de volume" (padrão ligado).
- Teclas tratadas no nível da Activity do leitor e consumidas (retorne `true`), para o volume do aparelho não mudar e o aviso de volume não aparecer. Fora do leitor, os botões voltam a controlar o volume.
- Os botões físicos de página dos Boox podem enviar teclas de página ou de volume, dependendo da configuração do aparelho: trate os dois.

### Renderização
- Extraia as páginas da página atual e das próximas para um cache em disco (LRU, tamanho por perfil).
- Use `BitmapRegionDecoder` para decodificar só a região do quadro, com `inSampleSize` adequado, e depois redimensione exatamente para o tamanho da tela antes dos filtros.
- Pré-carregue os 2 próximos quadros e a próxima página.
- Tablet: transição animada (~250 ms) de zoom/deslocamento entre quadros da mesma página, como câmera. E-ink: troca instantânea + `EinkController.onContentChanged()` e refresh completo a cada N trocas.
- Tela cheia imersiva; manter a tela ligada durante a leitura (ajustável).

### Indicadores (barra inferior discreta, pode ocultar)
Miniatura da página com o quadro atual destacado, "Página X / Y", "Quadro A / B", tracinhos de progresso dos quadros da página e a dica dos botões ("VOL + próximo · VOL − anterior") nas primeiras leituras.

### Progresso
Salvo a cada troca: `comicId`, página, quadro, modo, `updatedAt`, `deviceId`. Chegar ao último quadro marca como **Lido**. Reabrir continua de onde parou.

## 8. Biblioteca
- Cabeçalho: logo + wordmark "GIBIFLIX" (Anton), Buscar, Importar.
- Faixa do perfil quando e-ink: "Boox Go 7 Color detectado · perfil e-ink ativo" (toque leva a Ajustes).
- Abas: **Lendo**, **Todos**, **Séries**, **Coleções**.
- "Continuar lendo": capa, título, "Página X de Y · quadro A de B", barra de progresso, botão **Continuar**.
- Estante em grade: capa, título + número, estado. Colunas: 4 no Boox (retrato), 6–8 no tablet.
- Séries: agrupadas por Series/Volume, ordenadas por número, com "próximo não lido".
- Coleções: listas criadas pelo dono (ex.: "Para ler nas férias"); um quadrinho pode estar em várias.
- Ordenar por: lidos recentemente, adicionados recentemente, título, série e número.
- Buscar: título, série, autores e, com OCR, texto dos balões.
- Ações num quadrinho (toque longo): Ler, Preparar/Revisar quadros, Editar informações, Adicionar à coleção, Marcar como lido/não lido, Exportar, Excluir.
- Navegação inferior: **Biblioteca**, **Compartilhar** (enviar/receber/sincronizar), **Ajustes**.
- Tablet: tema escuro, capas maiores, efeito de destaque discreto ao focar (nada disso no e-ink).

## 9. Troca e sincronização entre aparelhos
### Rede local
- Tela **Compartilhar** com duas ações: **Enviar deste aparelho** e **Receber de outro aparelho**.
- Enviar: sobe um servidor Ktor CIO em porta aleatória, registra o serviço `_gibiflix._tcp` via `NsdManager` com o nome do aparelho (editável em Ajustes) e mostra um código de 6 dígitos. O servidor fica ativo enquanto a tela estiver aberta (serviço em primeiro plano com notificação).
- Receber: lista os aparelhos encontrados; o dono escolhe um e digita o código; o cliente recebe um token para as próximas chamadas na sessão.
- API JSON (prefixo `/api/v1`, header `Authorization: Bearer <token>`):
  - `GET /info` → nome, versão do app, perfil.
  - `POST /pair` `{code}` → `{token}`.
  - `GET /comics` → `[{id, sourceHash, title, series, number, sizeBytes, pages, preparedPages, reviewedPages, revision, updatedAt}]`.
  - `GET /comics/{id}/package` → o `.cbz` em streaming.
  - `GET /progress` e `POST /progress` → registros de progresso; a fusão usa o `updatedAt` mais recente por quadrinho.
  - `GET /bookmarks` e `POST /bookmarks` → mesma regra; exclusões viajam como registros com `deleted = true`.
- Tela de recebimento: grupos **Novos**, **Atualizados** (revisão remota maior que a local) e **Já tenho**, com caixas de seleção, "Baixar selecionados" e progresso por item. Ao fim, sincroniza progresso e marcadores nos dois sentidos.
- Botão **Sincronizar progresso** faz só a parte de progresso e marcadores.
- HTTP sem TLS na rede local é aceitável (uso pessoal, código de pareamento). Configure `networkSecurityConfig` permitindo tráfego sem TLS.

### Pasta
- **Exportar para pasta** (escolhida pelo seletor do Android): grava os pacotes selecionados e um `gibiflix-sync.json` com progresso e marcadores.
- **Importar de pasta**: lê pacotes e `gibiflix-sync.json` e funde. Serve para Syncthing, cabo USB ou pastas sincronizadas.

## 10. Servidores OPDS (Komga, Kavita e outros)
- Ajustes → Servidores: nome, URL do catálogo OPDS, usuário e senha opcionais (Basic Auth). Senhas guardadas com criptografia (Android Keystore).
- Exemplos de URL na tela de ajuda: Komga costuma ser `http://<ip>:25600/opds/v1.2/catalog`; Kavita mostra a URL OPDS (com a chave da API) no perfil do usuário.
- Navegar por feeds de navegação, buscar (OpenSearch quando o feed oferecer), ver capas, baixar pelo link de aquisição → importar → preparar.
- OPDS 1.2 (Atom/XML) obrigatório; OPDS 2.0 (JSON) se der tempo.

## 11. Marcadores, estatísticas e backup
- **Marcadores**: no menu do leitor, marca página e quadro atuais com nota opcional. Lista por quadrinho e lista geral; tocar abre no ponto exato.
- **Estatísticas** (Ajustes → Estatísticas): sessões de leitura (início, fim, páginas, quadros, aparelho). Tempo lido na semana e no mês, quadrinhos concluídos por mês (barras simples), sequência de dias lendo, tempo total por quadrinho. Sessões curtas (< 30 s) não contam.
- **Backup**:
  - Leve: zip com JSON de progresso, marcadores, coleções, estatísticas, ajustes e servidores (senhas só se o dono marcar a opção).
  - Completo: o leve mais todos os pacotes.
  - Restaurar funde com o que existe (o `updatedAt` mais recente vence).
  - Lembrete opcional semanal para fazer o backup leve.

## 12. OCR dos balões (Fase 8)
- Durante a preparação (opção "Ler o texto dos balões", padrão ligado no tablet): reconhecimento de texto do ML Kit em sua versão empacotada (modelo dentro do app, sem depender do Google Play Services) para alfabeto latino, rodado em cada quadro.
- Grava os blocos de texto com caixas normalizadas dentro do quadro no `gibiflix.json`.
- Usos: busca na biblioteca e dentro do quadrinho, e o botão **Ler texto** no leitor, que mostra o texto do quadro em letra grande. Isso ajuda muito em balões pequenos numa tela de 7".
- Tradução (opcional): ML Kit Translation no aparelho. Verifique se funciona nos Boox sem Play Services. Se não funcionar, a tradução é feita na preparação no tablet e gravada no pacote (`translations`), e os Boox só exibem.

## 13. Ajustes
Seções: Aparelho (perfil, nome na rede, Diagnóstico); Imagem (contraste, saturação, meios-tons, nitidez, pontilhado, prévia, restaurar padrões do perfil); Tela e-ink (refresh completo a cada N, limpeza por flash); Leitura (modo padrão, página inteira antes/depois, dividir quadros largos, margem do quadro, ampliação máxima, zonas de toque, direção padrão, manter tela ligada); Botões (usar volume, trocar, segurar para mudar de página); Preparação (preparar ao importar, OCR, tamanho de renderização de PDF, tolerância da detecção); Biblioteca (ordenação, colunas); Servidores; Estatísticas; Backup; Sobre (versão, licenças das fontes e bibliotecas).

## 14. Identidade visual
Veja `branding/README.md`. Resumo: ícone quadrado preto com G vermelho; tablet em tema escuro (preto puro, vermelho como acento); e-ink em tema claro (papel branco, tinta preta; vermelho só no colorido e com moderação). Wordmark "GIBIFLIX" em Anton; interface em Atkinson Hyperlegible.

A maquete conceitual aprovada tem quatro telas: biblioteca, leitura quadro a quadro e ajustes no Boox, e preparação no tablet. As descrições dos §§6, 7, 8 e 13 seguem essa maquete.

## 15. Fora do escopo
- Arquivos com DRM (compras da Amazon, Comixology etc.).
- Contas na nuvem e servidores próprios do Gibiflix.
- Leitura em rolagem vertical contínua (webtoon). Pode virar uma fase futura.
- Publicação na Play Store.
