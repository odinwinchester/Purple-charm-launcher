# Bootstrap de assinatura permanente do Purple Charm

Este PR ponte contém somente os workflows de geração e verificação do keystore permanente, o helper de cifra autenticada e os testes seguros. Não gera a chave, não publica APK e não altera a `main`. A execução manual só pode ser feita em `main`, dentro do environment `android-signing`, após merge aprovado deste bootstrap e revisão visual das proteções.

## Uma senha e backup

Use somente o secret existente `PURPLE_STORE_PASSWORD`, com pelo menos 20 caracteres, aleatório e exclusivo. Esse valor protege a senha do keystore, a chave Android e a recuperação do backup. O alias é fixo: `purple-charm-release`. O backup usa AES-256-GCM com chave derivada por scrypt. A mesma senha simplifica a configuração, mas concentra o risco: acesso à senha mais o ciphertext permite recuperar a chave; perder a senha inviabiliza a recuperação.

A senha só entra no runner como secret, fica em arquivo temporário com permissões `0600`, não aparece em argumentos de senha nem em logs. O keystore claro é apagado no fim. O artifact da geração contém apenas ciphertext cifrado, Base64 do ciphertext e fingerprint público. Qualquer execução anterior de geração bloqueia novas tentativas para evitar a criação silenciosa de uma identidade de assinatura diferente.

## Antes de aprovar ou gerar

1. Em **Settings → Branches**, proteja `main`: exija PR e checks; enquanto a branch estiver desprotegida, mudanças diretas podem alterar o workflow confiável que acessa secrets.
2. Em **Settings → Environments → android-signing**, permita somente a branch `main` e configure um ou mais Required reviewers confiáveis. O wait timer de um minuto não substitui aprovação humana. O bypass deve ficar desativado.
3. A variável `PURPLE_SIGNING_ENVIRONMENT_PROTECTED=YES` é apenas confirmação operacional e não prova as regras reais. Confira a tela antes de a configurar. Mantenha o secret `PURPLE_STORE_PASSWORD` somente neste environment.
4. Após o merge deste PR ponte e a confirmação da proteção, abra Actions → Preparar chave permanente → Run workflow, selecione `main` e digite `CREATE_PERMANENT_KEY`. Não use Re-run.
5. Baixe o artifact cifrado, copie o Base64 para o secret `PURPLE_KEYSTORE_CIPHERTEXT_B64`, salve a cópia `.pccks` em armazenamento privado e registre o SHA-256 do certificado na variável `PURPLE_SIGNING_CERT_SHA256`. Execute Verificar backup de assinatura antes de considerar a recuperação válida.

Este bootstrap não inclui a compilação release assinada. A integração posterior do PR #4 adicionará o workflow de APK estável e Gradle com o mesmo alias/senha. Até lá, não há APK assinado permanentemente disponível. A geração permanece bloqueada operacionalmente enquanto a proteção efetiva de `main` e do environment não for verificada por uma pessoa na interface do GitHub.
