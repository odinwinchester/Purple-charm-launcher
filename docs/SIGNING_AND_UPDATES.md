# Assinatura e atualização — Purple Charm Crystal Class

Identificador preservado: `com.purplecharm.launcher`. Os lançamentos futuros devem manter o mesmo keystore/certificado e alias `purple-charm-release`, com `versionCode` superior ao instalado. O único segredo de senha será `PURPLE_STORE_PASSWORD`, usado para o keystore, a chave e a recuperação AES-GCM.

Um APK atualiza uma instalação existente somente quando `applicationId` é igual, `versionCode` é aceito pelo Android e a assinatura é compatível. Um APK debug efêmero não garante atualização do APK já instalado no POCO. Não desinstale nem substitua a instalação atual com um APK cuja assinatura ainda não tenha sido comparada.

O fluxo estável restaura o keystore de `PURPLE_KEYSTORE_CIPHERTEXT_B64` usando a senha permanente, verifica `PURPLE_SIGNING_CERT_SHA256`, assina o build release e valida pacote, versão, assinatura, alinhamento e integridade antes de criar o artifact. As senhas não são argumentos da linha de comando; ficam em arquivos temporários privados ou variáveis de ambiente do job.

A proteção efetiva depende das regras do environment `android-signing` e da proteção da branch `main`. A variável `PURPLE_SIGNING_ENVIRONMENT_PROTECTED=YES` não comprova que reviewers ou restrição de branch estejam ativos. O proprietário deve confirmar isso visualmente. A mesma senha para keystore e backup reduz a separação de credenciais e concentra risco; usar senha aleatória longa e guardar a cópia cifrada em local privado.
