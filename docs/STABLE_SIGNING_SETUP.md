# Assinatura estável e recuperação do Purple Charm

Esta preparação implementa uma chave Android permanente e backup cifrado para uso futuro. Nenhuma chave definitiva foi gerada nesta etapa.

## Fluxo pronto

- .github/workflows/signing-crypto-selftest.yml executa em PR sem secrets e testa uma chave descartável.
- .github/workflows/generate-signing-keystore.yml gera JKS uma única vez em execução manual, com controle de senha já existente e apenas em android-signing. O artefato inclui ciphertext AES-256-GCM autenticado, Base64 desse ciphertext e fingerprint público.
- .github/workflows/verify-signing-recovery.yml valida manualmente a recuperação e o fingerprint, sem publicar keystore descriptografado.
- .github/workflows/stable-apk.yml restaura o keystore cifrado em diretório temporário, valida o fingerprint antes do build e confirma assinatura, pacote, versionCode, zipalign e integridade antes de publicar APK.
- scripts/keystore_crypto.py implementa criptografia autenticada com AES-GCM e chave derivada por scrypt.
- scripts/test_signing_crypto.sh testa ida e volta, igualdade binária e do certificado, rejeição de frase errada e detecção de ciphertext adulterado.

Somente o keystore descriptografado precisa existir temporariamente no runner isolado enquanto assina. Senhas são passadas por arquivos de modo 0600, não por argumentos nem logs. O helper não imprime conteúdo, senha ou payload. O keystore sem cifrar não é incluído em artifacts.

## Proteção a configurar no navegador Android

Abra o repositório → Settings → Environments → New environment e crie exatamente android-signing.

- Se disponível, configure Required reviewers, selecione revisor confiável e habilite prevenção de autoaprovação.
- Em Deployment branches and tags, permita somente main.
- Guarde todos os secrets no environment android-signing; não os configure no nível do repositório.
- Revise o commit e o workflow antes de aprovar cada execução manual.

Secrets necessários:

- PURPLE_STORE_PASSWORD: senha do keystore.
- PURPLE_KEY_PASSWORD: senha do alias.
- PURPLE_KEY_ALIAS: por exemplo purple-charm-release.
- PURPLE_RECOVERY_PASSPHRASE: frase forte e exclusiva, separada das senhas anteriores.
- PURPLE_KEYSTORE_CIPHERTEXT_B64: será preenchido após a primeira geração com o Base64 cifrado.

Variable pública:

- PURPLE_SIGNING_CERT_SHA256: fingerprint de 64 caracteres hexadecimais, que será entregue como arquivo no artifact de geração.
- PURPLE_SIGNING_ENVIRONMENT_PROTECTED: digite YES somente depois de configurar e salvar a proteção do environment.

Crie credenciais longas e exclusivas e guarde-as em seu gerenciador de senhas. Não as envie pelo chat. Se não houver revisor independente ou controle manual disponível, não execute assinatura até definir um controle adequado.

## Passos quando a branch padrão disponibilizar os workflows

O GitHub não permite acionar workflow_dispatch de um workflow que só existe na branch desta PR. Por isso, não execute geração de chave nem build de release antes da integração aprovada; esta preparação não altera main nem faz merge. O workflow de criptografia é seguro para PR: não recebe secrets.

1. Em Settings → Environments, configure android-signing e as proteções acima. Crie a variável PURPLE_SIGNING_ENVIRONMENT_PROTECTED=YES apenas depois de salvar as proteções. Crie STORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS e RECOVERY_PASSPHRASE como secrets no environment. Deixe KEYSTORE_CIPHERTEXT_B64 vazio até a geração.
2. Depois que os workflows existirem em main, abra Actions → Preparar chave permanente → Run workflow, selecione main, digite exatamente CREATE_PERMANENT_KEY e execute.
3. Aprove somente após conferir o commit/workflow. Baixe o artifact Purple-Charm-Encrypted-Signing-Backup.
4. Copie o conteúdo de keystore-backup.base64.txt para o secret PURPLE_KEYSTORE_CIPHERTEXT_B64. Copie certificate.sha256 para a variable PURPLE_SIGNING_CERT_SHA256.
5. Baixe keystore-backup.pccks pelo celular e guarde uma cópia em pasta privada do Google Drive. Preserve a frase de recuperação separadamente. Artifact expira em 90 dias; o backup no Drive e ciphertext no GitHub Secret são duráveis.
6. Após receber um artifact, não reexecute nem use Re-run na geração. Confirme que o ciphertext e fingerprint foram salvos antes de encerrar. A geração é bloqueada se o ciphertext já existe, ou se o Actions ainda registra uma execução bem-sucedida/artefato anterior. Se a execução falhar antes de produzir artifact, verifique que artifact não existe e o ciphertext segue vazio antes de considerar nova execução.
7. Execute Actions → Verificar backup de assinatura para confirmar restauração/fingerprint; o workflow não publica arquivos descriptografados.
8. Para assinar versão futura, Actions → APK estável assinado → Run workflow; selecione main e use versionCode maior que o instalado. Aguarde aprovação do environment antes de baixar o APK.

A cópia no Drive é cifrada com AES-256-GCM e só pode ser aberta com a frase de recuperação. O workflow Verificar backup de assinatura testa a recuperação e remove o arquivo temporário sem artifact. O fluxo estável reutiliza a mesma descriptografia temporária para assinar.

## Estado e limites

- A proteção real de android-signing depende da configuração na interface do GitHub e não pode ser confirmada por estes arquivos. Exija revisor e restrinja a main antes de adicionar secrets.
- A execução segura antes de merge tem um limite do GitHub: workflow_dispatch só funciona para arquivos de workflow presentes na branch padrão. O teste de PR não usa secrets e não gera chave de release.
- O ambiente configurado e protegido ainda não foi verificado. As chaves/secrets ainda não foram criados, nenhuma chave permanente/APK estável foi produzida, e o APK 0.7.1 do POCO não foi tocado.
- APK futuro terá atualização compatível apenas se a chave gerada estiver disponível e o versionCode for crescente. Não desinstale nem substitua o 0.7.1 nesta etapa.

