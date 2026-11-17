# Purple Charm v13 — atualização segura (infraestrutura)

Esta etapa prepara o código na branch development/v12-continuacao. O APK debug usa versionCode 13 e versionName 13; a validação do APK oficial continua limitada aos workflows protegidos da main e seus parâmetros de versão.

## Fluxo técnico

- A abertura do launcher agenda uma consulta imediata única e uma consulta periódica de 24 horas via WorkManager.
- Somente a API oficial de GitHub Releases é consultada. O parser existente descarta drafts, prereleases, versões não superiores e metadados inválidos.
- Quando há candidato válido, o download único é enfileirado com NetworkType.CONNECTED, que admite Wi-Fi ou rede móvel. O limite anunciado e absoluto é 100 MiB; o arquivo fica em cache privado e o SHA-256 detecta corrupção de transporte.
- Redirecionamentos são seguidos manualmente, limitados a cinco saltos, sempre em HTTPS e apenas para os hosts oficiais de assets GitHub explicitamente listados no código. Nenhum header de autenticação é enviado.
- Antes de manter o arquivo concluído, o app confere pacote, versionCode, versionName e certificados ativos do APK contra os do Purple Charm instalado. SHA-256 não é usado como prova de autenticidade.
- A infraestrutura cria apenas o Intent de instalação com URI FileProvider privada, permissão temporária de leitura e ACTION_INSTALL_PACKAGE. Uma tela deve chamar essa API após ação explícita do usuário; o Android mantém sua confirmação obrigatória. Se a permissão de origem não estiver habilitada, o helper abre as configurações específicas do app.

## Integração reservada para Etapa 3

O estado da versão disponível é salvo em preferências privadas e o WorkManager expõe progresso/estado. A tela Atualizações ainda precisa observar esses estados, apresentar versões e progresso, iniciar o Intent após toque do usuário, tratar retorno/cancelamento e limpar APKs antigos. Não foi alterado o menu inferior.

## Validação e limites

Testes locais cobrem política de URL/redirect e interrupção do stream. Os workflows da branch executam a suíte unitária e compilação debug; a checagem de metadados deve exigir package, code 13 e name 13. Nenhum APK foi instalado. A release pública v12 consultada anteriormente não possui asset APK, portanto não há download end-to-end real para testar. Verificação do certificado contra o APK oficial instalado, FileProvider, permissões, diálogo do instalador, rede móvel e comportamento do POCO exigem teste em aparelho. WorkManager pode adiar ou parar trabalho quando a rede some; o usuário poderá cancelar/recusar a instalação.

A comparação exata de certificados aceita o mesmo signer ativo, mas não tem suporte a rotação futura de certificado. Uma rotação precisará de validação explícita da lineage do Android.
