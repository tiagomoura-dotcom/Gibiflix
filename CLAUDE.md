# Gibiflix — instruções para o Claude Code

Gibiflix é um app Android pessoal: uma biblioteca de quadrinhos com leitor que mostra **um quadro por vez** (leitura guiada), otimizado para e-ink. O dono usa em três aparelhos:

| Aparelho | Tela | Papel principal |
|---|---|---|
| Samsung Galaxy Tab S11 | colorida, rápida | **preparar** os quadrinhos (detecção e revisão dos quadros) e ler |
| Onyx Boox Go 7 Color | e-ink colorido (Kaleido) | ler |
| Onyx Boox Go 7 | e-ink preto e branco | ler |

O dono não é desenvolvedor. Ele instala os APKs que o GitHub Actions gera e testa nos aparelhos. Explique o que você fez em linguagem simples ao fim de cada fase, incluindo o que ele deve testar.

## Leia antes de trabalhar
- `docs/ESPECIFICACAO.md` — o que o app faz, tela por tela, e as regras de comportamento.
- `docs/FORMATO_GIBIFLIX.md` — o pacote `.cbz` + `gibiflix.json`. É o contrato entre aparelhos: não quebre compatibilidade.
- `docs/ROTEIRO.md` — as fases. Trabalhe **uma fase por vez**, na ordem, e só considere a fase pronta quando todos os critérios de aceite forem cumpridos.
- `docs/COMO_COMECAR.md` — guia do dono (build, instalação). Atualize se mudar algo do fluxo.
- `branding/` — logo, ícone adaptativo pronto, cores e fontes.

## Stack
- Kotlin, Jetpack Compose (Material 3 com tema próprio), coroutines/Flow, Room, DataStore, WorkManager, kotlinx.serialization, Coil (miniaturas).
- Ktor: cliente (OPDS) e servidor CIO (troca entre aparelhos na rede local).
- Módulos:
  - `:core` — Kotlin/JVM puro, **sem Android**: detecção de quadros, filtros de imagem sobre `IntArray`, modelo e (de)serialização do `gibiflix.json`, ComicInfo.xml, parser OPDS (javax.xml.parsers/DOM, que existe no JVM e no Android), regras de sincronização. Tudo aqui tem teste unitário.
  - `:app` — Android: UI, banco, arquivos, rede, integração e-ink.
- Arquivos: CBZ com `java.util.zip`; CBR (incluindo RAR5) e CB7 com uma biblioteca baseada em libarchive (ex.: `me.zhanghai.android.libarchive`; confirme o artefato e a versão no Maven Central); PDF com `android.graphics.pdf.PdfRenderer`.
- minSdk 30. compileSdk/targetSdk: o estável mais recente suportado pelo AGP escolhido. JDK 17 (é o que o CI usa).
- APK só para `arm64-v8a` (os três aparelhos são ARM64), sem splits: um único APK universal para essa ABI.
- Versões centralizadas em `gradle/libs.versions.toml`. Use versões estáveis com pelo menos duas semanas de lançamento.

## Comandos
- Testes do núcleo (rodam sem Android SDK): `./gradlew :core:test`
- Completo: `./gradlew :core:test testDebugUnitTest assembleDebug`
- Se o seu ambiente não tiver Android SDK, rode pelo menos `:core:test` e deixe o CI compilar o `:app`. Antes de encerrar, revise com cuidado imports, nomes de recursos e APIs Android, porque o primeiro build real acontece no CI.
- Sempre commite `gradlew`, `gradlew.bat` e `gradle/wrapper/*`.

## CI, assinatura e versões
- `.github/workflows/android.yml` já existe. Push na `main` → testes + APK release assinado + GitHub Release `v0.1.<n>`. Pull request → testes + APK debug como artifact.
- Assinatura no `app/build.gradle.kts`, lida de variáveis de ambiente (o CI preenche a partir dos Secrets):

```kotlin
val ksFile = System.getenv("GIBIFLIX_KEYSTORE_FILE")
val ksPass = System.getenv("GIBIFLIX_KEYSTORE_PASSWORD")
android {
    defaultConfig {
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "0.1.$run"
    }
    signingConfigs {
        if (ksFile != null && ksPass != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = "gibiflix"
                keyPassword = ksPass
                storeType = "pkcs12"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false // ligar só depois de regras ProGuard testadas
        }
    }
}
```

- **Nunca** commite keystore, senha, `.p12`, `.jks` ou `.keystore` (o `.gitignore` já bloqueia).
- Se o CI mostrar avisos de versão de actions (Node depreciado etc.), atualize as versões das actions no workflow.

## Regras de arquitetura
- `device/DeviceProfile` decide o perfil: `TABLET`, `EINK_COLOR` ou `EINK_MONO`. Toda decisão "é e-ink?" passa por ele. Não espalhe checagens de `Build.*` pelo código.
- Controle de refresh e-ink atrás da interface `EinkController` (implementação Onyx + implementação genérica). O app precisa compilar e funcionar mesmo que o SDK da Onyx falhe ou não exista no aparelho.
- Coordenadas de quadros sempre **normalizadas (0..1)** em relação à página original.
- O pacote `.cbz` é a fonte da verdade sobre páginas e quadros. O Room é índice/cache mais os dados pessoais (progresso, marcadores, coleções, estatísticas).
- Filtros de imagem são aplicados **na exibição**, por perfil. Nunca grave filtros no pacote.
- Nada pesado na main thread. Preparação em WorkManager com notificação de progresso, retomável.
- Gravação de pacote: escreva num arquivo temporário e renomeie no fim (nunca deixe um `.cbz` pela metade).

## E-ink (vale para os perfis EINK_*)
- Sem animações, ripples, sombras, gradientes ou fundos escuros grandes.
- Alto contraste, áreas de toque generosas.
- No `EINK_MONO`, nenhuma informação pode depender só de cor: use inversão, ícone ou texto.

## Convenções
- Código e identificadores em inglês. Textos da interface em português do Brasil, sempre em `strings.xml`.
- Commits pequenos, mensagens em português no imperativo ("Adiciona leitor de CBZ").
- Teste unitário para toda lógica do `:core`. Imagens de teste são geradas no próprio teste (sem binários no repositório).
- Ao fim de cada fase, atualize a seção "Estado atual" no fim de `docs/ROTEIRO.md`.
- Na dúvida sobre algo que a especificação não cobre, pergunte ao dono antes de decidir.
