# Assinatura estável e recuperação do Purple Charm

Esta configuração prepara uma assinatura permanente. A chave definitiva ainda não foi gerada. O único segredo de senha é `PURPLE_STORE_PASSWORD`; o mesmo valor protege o keystore, a chave Android e o backup cifrado. O alias fixo é `purple-charm-release`.

## Fluxo implementado

- `.github/workflows/generate-signing-keystore.yml` é manual, exige `main`, environment `android-signing`, confirmação explícita, senha longa e verifica que não houve geração anterior nem artefato prévio. Uma geração anterior bloqueia novas tentativas para evitar substituição silenciosa.
- `.github/workflows/verify-signing-recovery.yml` restaura o backup com a mesma senha, verifica o alias e compara o certificado com o fingerprint protegido; não publica o keystore restaurado.
- `.github/workflows/stable-apk.yml` executa testes antes de acessar o keystore, restaura a mesma cópia cifrada, confere o SHA-256 antes e depois de assinar e valida pacote, `versionCode`, alinhamento e integridade do APK.
- `scripts/keystore_crypto.py` cifra com AES-256-GCM e deriva a chave por scrypt.
- `scripts/test_signing_crypto.sh` usa keystore descartável, uma só senha e o alias fixo.

A senha só entra no runner como secret, é gravada em arquivo temporário de modo `0600` e não é interpolada em argumentos de senha da linha de comando. O `keytool` recebe o caminho do arquivo (`-storepass:file`/`-keypass:file`); o Gradle recebe a senha em variável de ambiente. O keystore claro só existe temporariamente no runner isolado e é removido ao final. Artefatos de recuperação incluem apenas ciphertext AES-GCM, sua codificação Base64 e fingerprint público.

## Configuração pelo GitHub no Android

1. Abra o repositório → **Settings → Environments → android-signing**. Confira na tela que `main` é a única branch autorizada e configure um ou mais **Required reviewers** confiáveis, com prevenção de autoaprovação quando disponível. O wait timer de 1 minuto e a opção de bypass desativada são controles adicionais; o timer sozinho não é aprovação humana.
2. Mantenha `PURPLE_SIGNING_ENVIRONMENT_PROTECTED=YES` somente depois de confirmar visualmente essas regras. A variável é um reconhecimento manual e não prova a proteção real. Não coloque segredos no escopo do repositório.
3. Mantenha o secret existente `PURPLE_STORE_PASSWORD`. Ele deve ser forte, aleatório, exclusivo e ter pelo menos 20 caracteres. Guarde-o em um gerenciador de senhas e mantenha uma cópia de recuperação segura. Não envie a senha pelo chat.
4. Mantenha a variable pública `PURPLE_SIGNING_CERT_SHA256` vazia até criar a chave; depois copie para ela o SHA-256 fornecido pelo workflow. Mantenha o secret `PURPLE_KEYSTORE_CIPHERTEXT_B64` vazio até a geração. O alias não é secret e não precisa de configuração.
5. Não inicie a geração até que estes workflows estejam na branch padrão `main`, a proteção do environment tenha Required reviewers e as configurações tenham sido revisadas. A geração é manual e exige o texto `CREATE_PERMANENT_KEY`.
6. Baixe `Purple-Charm-Encrypted-Signing-Backup`; guarde o arquivo `.pccks` em armazenamento privado e copie o conteúdo do arquivo Base64 para `PURPLE_KEYSTORE_CIPHERTEXT_B64`. Copie o fingerprint para `PURPLE_SIGNING_CERT_SHA256`. O artifact expira; a cópia privada e o secret são necessários para recuperação e releases.
7. Execute manualmente **Verificar backup de assinatura**. Só depois de essa validação passar considere o backup provisionado. Para releases, use sempre o workflow estável, mesma senha, mesmo keystore/alias e `versionCode` crescente.

## Risco concentrado e limites verificados

Usar uma única senha simplifica a administração, mas concentra riscos: quem obtiver a senha e o backup cifrado pode recuperar a chave de assinatura, e a senha perdida torna o backup irrecuperável. O ciphertext também permite tentativa offline de senhas; por isso a senha precisa ser aleatória e longa. A cópia de recuperação deve ficar em conta privada e protegida por autenticação forte.

A configuração efetiva de reviewers e regras de branches é administrada pela interface GitHub e não fica provada pela variável `PURPLE_SIGNING_ENVIRONMENT_PROTECTED`. A automação local nesta etapa não teve acesso para confirmar as regras salvas. Além disso, a `main` estava sem branch protection na última leitura disponível; isso permite alterações diretas à branch padrão e reduz a garantia de revisão. Confirme o estado atual em **Settings → Branches** e habilite proteção para exigir PR e checks antes de usar qualquer segredo.

Os workflows `workflow_dispatch` não podem ser executados a partir da branch desta PR enquanto não estiverem na default branch. Não faça merge integral do PR #4 apenas para desbloquear a geração: a opção é uma PR separada, mínima, contendo apenas a infraestrutura de assinatura e sua documentação, revisada e testada. Esta etapa não altera `main`, não gera a chave definitiva nem APK estável.
