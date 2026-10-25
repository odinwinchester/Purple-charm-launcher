# Bot de build e lançamentos do Purple Charm

O bot combina validação manualmente acionada, APK de teste, assinatura estável opcional e publicação oficial aprovada.

## Fluxos

- O workflow Purple Charm Release Bot só aceita execução manual em main. Sempre executa integridade das artes, testes unitários e build de APK debug para teste; publica APK e release-metadata.json como artifact sem assinatura estável. A assinatura desse artefato é identificada como DEBUG_EPHEMERAL_TEST_ONLY.
- Se publish_release=false, o fluxo termina após fornecer o APK de teste para validação. Esse é o padrão.
- Se publish_release=true, o job sign chama o workflow estável reutilizado, que exige android-signing, descriptografa o mesmo keystore, confere o certificado antes e depois do build e publica só o APK e metadados validados como artifact.
- A publicação do GitHub Release exige a aprovação do ambiente android-release. O workflow de publicação também exige a variável PURPLE_RELEASE_ENVIRONMENT_PROTECTED=YES e não sobrescreve tag/release existentes.
- Após uma release oficial criada com o marcador de homologação, o workflow Discord anuncia o APK e registra uma marca no corpo da release antes do POST. Reruns reconhecem a marca e não enviam anúncio duplicado.

## Aprovação e configuração manual

No navegador Android, crie o environment android-release em Settings → Environments. Quando disponível, habilite Required reviewers e restrinja branches/tags de deploy a main. Configure a variável de environment PURPLE_RELEASE_ENVIRONMENT_PROTECTED=YES somente após salvar as regras.

Confirme o environment android-signing conforme docs/STABLE_SIGNING_SETUP.md. O fluxo usa um secret de senha (`PURPLE_STORE_PASSWORD`), o keystore cifrado (`PURPLE_KEYSTORE_CIPHERTEXT_B64`) e a variable pública do SHA-256; o alias é fixo. A mesma senha protege o backup, concentrando o risco de acesso. A URL secreta do Discord continua sendo DISCORD_WEBHOOK_URL e deve ficar como secret do repositório ou environment apropriado; ela nunca é usada nos testes.

## Como operar depois que o workflow estiver na branch padrão

GitHub oferece workflow_dispatch somente a workflows presentes na branch padrão. Portanto, estes workflows não serão acionados manualmente antes da integração aprovada do PR #4.

1. Actions → Purple Charm Release Bot → Run workflow, branch main, versionCode maior do que o atual (mínimo 10).
2. Primeiro execute com publish_release=false. Baixe o artifact Purple-Charm-Test-v...-code... e instale apenas se deseja teste local. É APK debug efêmero; não atualiza de forma garantida a instalação atual e não deve ser apresentado como versão estável.
3. Homologue o resultado no POCO e valide a assinatura permanente já provisionada.
4. Para uma versão oficial, execute novamente com publish_release=true. Aguarde primeiro aprovação do environment android-signing e depois android-release. Só então será criado o GitHub Release.
5. A publicação de release dispara o anúncio do Discord uma única vez. Se a chamada externa falhar depois da gravação da trava, o sistema escolhe não repetir para evitar duplicatas; confira a release e publique manualmente se o anúncio não apareceu.

## Dados de auditoria

Cada artifact inclui release-metadata.json com versionName, versionCode, applicationId, SHA do commit, data UTC, contagem de testes, resultado de validação, SHA-256 do APK e SHA-256 do certificado. O release oficial inclui o APK assinado e esse arquivo de metadados.

Nenhum build executa automaticamente a publicação de release por push. Nenhum teste chama o webhook. O APK de teste usa assinatura debug efêmera e não substitui a instalação 0.7.1 no POCO.