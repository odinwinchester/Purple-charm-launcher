# Bootstrap de assinatura permanente do Purple Charm

Este PR ponte contém apenas geração e validação de recuperação do keystore. Não gera a chave nesta revisão, não publica APK e não altera a `main`. O check obrigatório `safe-selftests` roda em todo PR destinado a `main`, sem filtro de caminhos, e não recebe secrets.

## Uma senha e backup

Use somente `PURPLE_STORE_PASSWORD`, com pelo menos 20 caracteres, aleatório e exclusivo. Essa senha protege o keystore, a chave Android e o backup. O alias é fixo: `purple-charm-release`. O backup usa AES-256-GCM e scrypt. Isso simplifica a administração, mas concentra o risco: quem obtiver a senha e o ciphertext pode recuperar a chave; perder a senha impede a recuperação.

A senha é passada ao runner como secret, gravada em arquivo temporário `0600` e não é usada como argumento de senha na linha de comando. O helper criptográfico cria os arquivos de saída atomicamente e falha se o destino já existir. O artifact contém somente ciphertext, Base64 do ciphertext e fingerprint público.

## Geração única e falhas

O workflow exige execução manual em `main`, confirmação `CREATE_PERMANENT_KEY` e environment `android-signing`. Bloqueia qualquer execução anterior, reexecução do mesmo run, artifact anterior, ciphertext existente ou fingerprint já configurado. Assim, uma segunda chave não substitui silenciosamente a primeira.

O upload do artifact tenta rodar mesmo após falha recuperável e inclui apenas arquivos cifrados e o fingerprint. Uma interrupção abrupta antes de esse ciphertext ser persistido ainda pode deixar a chave temporária irrecuperável. Se uma execução falhar ou for cancelada, não use Re-run nem inicie outra geração: examine o run e seus artifacts; valide qualquer recuperação pelo workflow de verificação. Se nenhum backup válido existir, preserve o histórico e peça revisão manual antes de qualquer nova tentativa.

## Regras atuais e passos manuais

A API do GitHub confirma o ruleset ativo `Protecao-main-Purple-Charm`: exige PR e o check `safe-selftests`, proíbe exclusão/force-push, mas configura **zero aprovações obrigatórias**. O workflow sem filtro de paths corrige o risco de check ausente que bloquearia PRs alheios à assinatura. Nenhuma regra do repositório foi alterada nesta missão.

A configuração atual do environment `android-signing` não pôde ser lida pela integração. Antes de qualquer geração, confira pelo navegador em **Settings → Environments → android-signing**: permita apenas `main` e configure Required reviewers confiáveis. O wait timer de um minuto não equivale a aprovação humana. Mantenha bypass desativado. A variável `PURPLE_SIGNING_ENVIRONMENT_PROTECTED=YES` é só uma confirmação operacional, não prova essas regras. O secret `PURPLE_STORE_PASSWORD` deve existir apenas nesse environment.

Depois que o PR #5 for aprovado e integrado, a geração estará disponível em **Actions → Preparar chave permanente**. Baixe o artifact cifrado, copie o Base64 para `PURPLE_KEYSTORE_CIPHERTEXT_B64`, guarde o `.pccks` em armazenamento privado e registre o fingerprint em `PURPLE_SIGNING_CERT_SHA256`. Execute **Verificar backup de assinatura** antes de usar o keystore.

Este bootstrap não inclui o APK release assinado. O PR #4 contém o fluxo estável que usa o mesmo alias, senha, ciphertext e fingerprint. A compatibilidade foi verificada por inspeção de código; a assinatura permanente continua pendente até gerar a chave e testar a validação de recuperação. Não executar workflow que acesse secrets antes da revisão humana das proteções do ruleset e do environment.
