# Assinatura e atualização — Crystal Class 0.7.1

Identificador preservado: com.purplecharm.launcher. Versão0.7.1, versionCode padrão8 (anterior7). Versões futuras devem usar código superior ao instalado, sem mudar applicationId.

## Diagnóstico e limite

O workflow antigo somente executava assembleDebug, sem signingConfig persistente. Um runner efêmero pode gerar uma nova chave debug. Certificados públicos no APK/log não permitem recuperar a chave privada. Um código maior sozinho não permite atualizar APK assinado por outra chave.

O workflow android.yml continua oferecendo APK DEBUG-EPHEMERAL, explicitamente diagnóstico; não promete atualização sobre instalações anteriores. Não reutilizar cache público de Actions nem artefatos para guardar chave privada. Nunca desinstalar automaticamente.

## Processo estável preparado

Arquivo .github/workflows/stable-apk.yml: execução manual, environment android-signing, testes primeiro, assembleRelease assinado, verificação obrigatória do certificado SHA-256 esperado, pacote e versão, ZIP/alinhamento e artefato. A chave existe somente em RUNNER_TEMP durante o job e é removida ao final. Sem chave/senha em Git.

Configurar o environment android-signing no GitHub com proteção de execução/revisores e acesso somente a refs confiáveis. Não executar código de PRs desconhecidos com secrets. Configurar nele:

- Secret PURPLE_KEYSTORE_BASE64: keystore privado codificado em base64.
- Secrets PURPLE_STORE_PASSWORD, PURPLE_KEY_ALIAS, PURPLE_KEY_PASSWORD.
- Variable PURPLE_SIGNING_CERT_SHA256: fingerprint público confiável da chave escolhida,64 hexadecimais. A verificação impede distribuir APK com outra chave por engano.

O proprietário deve guardar a chave/senhas com backup privado seguro e usar sempre a mesma chave. Não enviar segredos pelo chat. A integração disponível nesta sessão não permite criar secrets/environments; provisionamento e execução assinada estável permanecem pendentes. Não foi gerada nem publicada uma chave privada.

O workflow novo deve estar disponível em uma ref reconhecida pelo Actions para dispatch; enquanto não estiver na default branch, é possível compilar localmente esta branch com as quatro variáveis PURPLE_KEYSTORE_PATH/PURPLE_STORE_PASSWORD/PURPLE_KEY_ALIAS/PURPLE_KEY_PASSWORD e PURPLE_VERSION_CODE. A chave deve ser o mesmo arquivo privado; nunca adicioná-la ao repositório. Não promover main só para liberar o workflow antes do aceite físico.

## Compatibilidade e migração segura

1. Identificar o APK instalado e conferir seu certificado público com apksigner verify --print-certs; registrar versionCode.
2. Se existir a chave privada correspondente, usá-la no environment protegido. Isso permite produzir atualização compatível, a validar com adb install -r (sem -d), preservando dados.
3. Se a chave anterior não estiver disponível, não prometer instalação por cima. Preservar a instalação atual e procurar o keystore original antes de migrar.
4. Com assinaturas incompatíveis, só migrar após ter uma exportação legível e validada das preferências/posições/pastas e cópia dos arquivos/URIs externos. O app atual tem allowBackup=false; não assumir que adb backup recupera esses dados. Esta revisão não implementa exportação/importação nova e não recomenda desinstalar antes de existir cópia/restauração comprovadas.
5. Após uma instalação compatível, validar no POCO nomes, posições, pastas, cidade, arte, wallpaper e gestos. O tipo debug/release não substitui o requisito de mesma assinatura; também manter compatibilidade do atributo debuggable em testes reais.

Nenhum update sem desinstalação foi validado fisicamente nesta sessão. O pipeline estável somente poderá ser declarado operacional após provisionar os secrets, executar e validar o APK resultante.
