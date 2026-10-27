# Identidade visual do Gibiflix

## Logo
Quadrado preto com um G vermelho, condensado e pesado (formato de "estádio": topo e base em semicírculo, barra horizontal entrando no miolo).

| Arquivo | Uso |
|---|---|
| `gibiflix_logo.svg` / `gibiflix_logo_512.png` | Logo principal (README, tela Sobre, splash no tablet e no e-ink colorido) |
| `gibiflix_logo_mono.svg` / `gibiflix_logo_mono_512.png` | Versão para e-ink preto e branco (G branco) |
| `gibiflix_g.svg` | Só a letra, com `currentColor`, para usar dentro da interface |
| `android/` | Ícone adaptativo pronto: copie cada pasta para `app/src/main/res/` |

O path do G (quadro de 512 × 512):

```
M351 201A95 95 0 0 0 161 201L161 311A95 95 0 0 0 351 311L351 254L262 254L262 311L295 311A39 39 0 0 1 217 311L217 201A39 39 0 0 1 295 201Z
```

## Cores

| Nome | Hex | Onde |
|---|---|---|
| Preto Gibiflix | `#000000` | Fundo do ícone; fundo do tema do tablet (AMOLED) |
| Vermelho Gibiflix | `#E1261C` | G do logo, wordmark, botão principal, aba ativa (tablet e e-ink colorido) |
| Superfície escura | `#161616` | Cartões e barras no tema do tablet |
| Texto secundário escuro | `#B5B5B5` | Texto de apoio no tema do tablet |
| Papel | `#FFFFFF` | Fundo dos temas e-ink (o branco do e-ink já é "papel") |
| Tinta | `#000000` | Texto e linhas nos temas e-ink |
| Azul detecção | `#1F5FA8` | Contorno dos quadros detectados no editor |
| Laranja seleção | `#D9731A` | Quadro selecionado no editor |

No e-ink preto e branco, o vermelho vira preto: estados ativos usam inversão (fundo preto, texto branco), nunca só cor.

## Tipografia
- **Anton** (Google Fonts, licença OFL): wordmark "GIBIFLIX" e títulos grandes. Baseline reta, levemente espaçada.
- **Atkinson Hyperlegible** (Google Fonts, licença OFL): todo o resto da interface. Foi desenhada para máxima legibilidade, o que ajuda muito numa tela e-ink de 7".

Baixe os .ttf do repositório `google/fonts` no GitHub, coloque em `app/src/main/res/font/` e inclua o arquivo OFL.txt em `app/src/main/assets/licenses/`.
