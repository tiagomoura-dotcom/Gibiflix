# Formato do pacote Gibiflix (versão 1)

O pacote é um `.cbz` comum, então qualquer outro leitor de quadrinhos ainda consegue abri-lo. O Gibiflix acrescenta um arquivo `gibiflix.json` com os quadros e o resto dos dados da preparação.

## Estrutura do zip

```
<id>.cbz
├── 0001.jpg
├── 0002.jpg
├── …              páginas, nomes com zeros à esquerda, na ordem de leitura
├── ComicInfo.xml  opcional, preservado do original
└── gibiflix.json
```

- As páginas são gravadas **sem compressão** (método STORED), porque JPEG/PNG/WebP já são comprimidos e assim o acesso fica mais rápido. `gibiflix.json` e `ComicInfo.xml` usam DEFLATE.
- A ordem das páginas é a ordem dos nomes, que o Gibiflix normaliza na importação.

## gibiflix.json

```json
{
  "format": "gibiflix",
  "version": 1,
  "id": "3f2a8c1e-5b7d-4e0a-9c11-2d4f6a8b0c9e",
  "revision": 7,
  "sourceHash": "sha256:9b1c…",
  "title": "Título do quadrinho",
  "readingDirection": "ltr",
  "createdAt": "2026-10-27T12:00:00Z",
  "updatedAt": "2026-10-27T13:10:00Z",
  "createdBy": { "app": "0.1.42", "device": "Galaxy Tab S11", "profile": "TABLET" },
  "detector": { "name": "xycut", "version": 1 },
  "pages": [
    {
      "file": "0001.jpg",
      "width": 1988,
      "height": 3056,
      "spread": false,
      "readingDirection": null,
      "trim": { "x": 0.021, "y": 0.015, "w": 0.958, "h": 0.97 },
      "confidence": 0.92,
      "reviewed": true,
      "panels": [
        {
          "x": 0.031, "y": 0.025, "w": 0.94, "h": 0.29,
          "source": "auto",
          "text": [
            { "x": 0.10, "y": 0.05, "w": 0.30, "h": 0.12, "value": "Ouviu isso?" }
          ],
          "translations": {
            "en": [ { "x": 0.10, "y": 0.05, "w": 0.30, "h": 0.12, "value": "Did you hear that?" } ]
          }
        }
      ]
    }
  ]
}
```

### Campos
| Campo | Regra |
|---|---|
| `format`, `version` | Sempre `"gibiflix"` e o inteiro da versão. |
| `id` | UUID gerado na primeira preparação. Identifica o quadrinho em todos os aparelhos. |
| `revision` | Inteiro que sobe a cada alteração salva (preparação ou editor). Usado para saber se um aparelho tem uma versão mais nova. |
| `sourceHash` | SHA-256 do arquivo original importado. Detecta duplicados. |
| `readingDirection` | `"ltr"` (ocidental) ou `"rtl"` (mangá). Uma página pode sobrescrever com o próprio `readingDirection`; `null` herda do livro. |
| `pages[].width/height` | Tamanho em pixels da imagem da página no pacote. |
| `pages[].spread` | `true` para página dupla. |
| `pages[].trim` | Retângulo do conteúdo para o modo página. Ausente = página inteira. |
| `pages[].confidence` | 0..1, da detecção automática. |
| `pages[].reviewed` | `true` quando o dono aprovou a página no editor. |
| `pages[].panels` | Quadros **na ordem de leitura**. Lista vazia = página sem quadros (ex.: capa); no modo quadro a quadro ela é mostrada inteira. |
| `panels[].x/y/w/h` | Normalizados 0..1 em relação à página (`x`,`y` = canto superior esquerdo). |
| `panels[].source` | `"auto"` (detecção) ou `"manual"` (criado ou alterado no editor). |
| `panels[].text` | Opcional (OCR). Caixas normalizadas **em relação ao quadro**. |
| `panels[].translations` | Opcional. Mapa idioma → blocos, no mesmo formato de `text`. |

## Regras de compatibilidade
- Um leitor que encontrar `version` maior que a que conhece ignora o `gibiflix.json`, trata o arquivo como CBZ comum e oferece "Preparar de novo".
- Campos desconhecidos devem ser **preservados** ao regravar o arquivo (guarde o JSON original e mescle).
- Mudanças que adicionam campos opcionais não mudam `version`. Mudanças que alteram o significado de um campo existente mudam.
- Gravação atômica: escreva o novo `.cbz` num temporário na mesma pasta e renomeie por cima do antigo.
