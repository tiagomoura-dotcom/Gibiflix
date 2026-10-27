# Como começar (guia do dono)

Você não precisa instalar nada no computador. Tudo acontece em três lugares: o site do GitHub (onde o código fica e o APK é gerado), o Claude Code (que escreve o código) e os seus aparelhos (onde você instala e testa).

## 1. Criar o repositório
1. Entre em github.com (crie uma conta, se não tiver).
2. Abra github.com/new.
3. Nome: `gibiflix`.
4. Visibilidade: **Public** é o mais simples (compilação ilimitada e o APK baixa sem login). Seus quadrinhos nunca vão para o GitHub, só o código. Se preferir **Private**, também funciona; o download do APK exigirá estar logado.
5. **Não** marque README, .gitignore nem licença. Clique em **Create repository**.

## 2. Enviar o pacote inicial
1. Na página do repositório vazio, toque no link **uploading an existing file**.
2. Escolha `gibiflix-starter.zip` (não descompacte) e toque em **Commit changes**.

## 3. Abrir no Claude Code
1. Abra o Claude Code (claude.ai/code no navegador ou a área de código do app do Claude).
2. Conecte sua conta do GitHub e dê acesso ao repositório `gibiflix` quando ele pedir.
3. Selecione o repositório e cole o **Prompt 1** (abaixo).

### Prompt 1 (só na primeira vez)
```
O repositório contém gibiflix-starter.zip com a especificação do app Gibiflix.
1. Descompacte o zip na raiz do repositório (unzip -o; se não houver unzip, use python3 -m zipfile -e), sobrescrevendo o que existir. Apague o zip e faça commit.
2. Leia o CLAUDE.md e todos os arquivos em docs/.
3. Execute a Fase 0 do docs/ROTEIRO.md.
```

### Prompt das próximas fases
```
Execute a próxima fase do docs/ROTEIRO.md.
```

## 4. Cadastrar a chave de assinatura (uma vez, na Fase 0)
O Claude Code vai te mostrar dois valores: um texto longo (a chave) e uma senha. No GitHub:
1. Repositório → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Nome `GIBIFLIX_KEYSTORE_BASE64`, valor: o texto longo. Salve.
3. Nome `GIBIFLIX_KEYSTORE_PASSWORD`, valor: a senha. Salve.
4. **Guarde uma cópia dos dois** num lugar seguro (gerenciador de senhas ou nota privada). O GitHub não deixa ver os secrets depois. Se a chave se perder, as próximas versões só instalam depois de desinstalar o app, o que apaga a biblioteca.

## 5. O ciclo de cada fase
1. O Claude Code trabalha numa branch e abre um **pull request**.
2. No GitHub, abra o pull request e espere o check do Actions ficar verde.
3. Toque em **Merge pull request** → **Confirm merge**.
4. O Actions compila a `main` e cria um **Release** com o APK (leva alguns minutos). Ele aparece em `github.com/<seu-usuário>/gibiflix/releases`.

Se o check ficar vermelho: abra a aba **Actions**, entre no build que falhou, copie as últimas linhas do erro e cole no Claude Code com "O CI falhou com este erro:".

## 6. Instalar e atualizar nos aparelhos
**Mais simples:** no navegador do aparelho, abra `github.com/<seu-usuário>/gibiflix/releases/latest`, baixe o `.apk` e instale. Na primeira vez, o Android pede para permitir "instalar apps desconhecidos" para o navegador.

**Recomendado (atualiza sozinho):** instale o app **Obtainium** (código aberto, baixado pelo GitHub dele) no tablet e nos dois Boox, toque em "Adicionar app" e cole `https://github.com/<seu-usuário>/gibiflix`. A cada nova versão, ele avisa e instala. Repositório privado exige um token do GitHub nas configurações do Obtainium.

Primeira abertura em cada Boox: confirme se a tela é colorida ou preto e branco. Depois abra **Ajustes → Diagnóstico**, toque em **Copiar** e cole no Claude Code para ele registrar o modelo do aparelho.

## 7. Dicas
- Teste cada fase nos três aparelhos e conte ao Claude Code o que achou, em linguagem normal ("no Go 7 a imagem ficou clara demais").
- Mudanças pequenas não precisam esperar uma fase: peça direto ("deixe o botão Continuar maior").
- Se o Boox estiver com fantasmas na tela, ajuste primeiro em Ajustes → Tela e-ink dentro do Gibiflix, antes de mexer nas otimizações da Onyx.
