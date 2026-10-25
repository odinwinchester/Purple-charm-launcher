# Correções finais após novo teste real — PR #4

## Estado de entrada

Branch existente codex/final-consolidation, PR #4, head c18c91f5f58ccaf10ea12995d425ff5eab0113e9; main c07c643ce2edbdfbfa580d2461e771b96aa2f13c. Base reversível preservada nos commits e no backup anterior da main. Nenhuma implementação paralela ou merge na implementação.

O proprietário confirmou no POCO: menu fumê, criação/pastas com vários membros, arraste entre páginas, persistência, clima e fluidez. Esta rodada preserva esses mecanismos. Máscara de ícones mantida sem mudanças;80 recursos gráficos byte a byte intactos, sem WebP/GIF/PNG alterado.

## Diagnóstico e correções

- Gaveta criava uma GridLayout por letra; cada grupo iniciava uma nova linha, mesmo com células livres na anterior. Substituída a composição por uma única DrawerAppGrid por categoria, sequencial da esquerda para a direita. Colunas calculadas por largura (1..5), linha uniforme baseada na altura medida dos ícones/nomes, sem padding de seções. Índice A–Z usa a posição real do primeiro item de cada letra.
- Cabeçalho usa altura medida, nomes das categorias em uma linha com rolagem horizontal; pesquisa compactada e separada do viewport por layout vertical com peso. Mesma lógica para Tudo/Comunicação/Entretenimento/Outros. Rolar e selecionar a categoria já ativa não recriam itens.
- A pasta usava Dialog com janela própria e tentava reiniciar drag na home após dismiss. Agora somente a apresentação é FolderSurface no mesmo host da home: modelo, edições e persistência originais não reescritos. O membro mantém fonte anexada enquanto o painel recolhe ao sair dos limites. Modo explícito Arrastar para início também preservado. O root encaminha coordenadas traduzidas ao controlador WorkspaceDragLayout existente, sem reiniciar a sessão.
- Drop válido usa gravação atômica anterior, remove membro da pasta, elimina pasta que perder último membro e fecha painel. Cancelar restaura painel e preferências sem escrita. Mais de seis membros continuam acessíveis/persistidos.
- Configurações do sistema removida do menu fumê; Sistema no menu inferior permanece como acesso ao Android. Tema/Launcher e informações/desinstalação por aplicativo continuam distintos e preservados.
- Removidos somente entrada, Activity/manifest e código da importação Retro0.4. Nenhum wallpaper, moldura, preferência ou arquivo pessoal importado anteriormente foi apagado.
- SmokeUi/SmokeDialogBuilder compartilham painel preto arroxeado translúcido, contorno fino e seleção violeta nos diálogos internos. Tema/Launcher, wallpapers, ações e localização usam o padrão onde compatível. Configurações Android, seletor do sistema, apps externos e dock não foram redesenhados.

## Assinatura

applicationId com.purplecharm.launcher mantido; versionName0.7.1, versionCode8 padrão, anterior7. Comparação dos certificados comprovou chaves debug distintas nos builds anteriores. O APK diagnóstico continua DEBUG-EPHEMERAL e não promete atualizar instalação com outra assinatura.

Processo estável preparado em .github/workflows/stable-apk.yml, por workflow_dispatch/environment android-signing: quatro secrets privados e fingerprint público esperado, teste antes da chave, signingConfig release, verificação de certificado/pacote/código e limpeza do keystore do runner. Nenhuma chave/senha privada foi adicionada ao Git. Provisionamento dos secrets e execução com a chave correspondente ao app instalado ainda pendentes; integração disponível não permite administrar secrets. Ver docs/SIGNING_AND_UPDATES.md para compatibilidade, proteção e resgate não destrutivo de preferências de builds debug.

Não foi validado update sem desinstalação no POCO. Não foi desinstalado nem alterado aplicativo no aparelho. Se não existir a chave antiga, aguardar backup/restauração comprovados antes de migrar.

## Verificações e histórico real

- Verificador local80/80 imagens e26/26 artes aprovado; diff sem modificações nos recursos gráficos.
- Primeira execução compilou mas testes apontaram fonte de drag sem medida no cenário simulado e amostra da gaveta que agora cabe inteira sem rolagem. Cenários corrigidos medindo a fonte e usando260 apps no teste de índice.
- Run40155118888 bloqueado antes dos testes por Maven Central HTTP429, não por erro Kotlin.
- Run40155221591:78/79 passaram; teste nativo de extração detectou drop não encaminhado após recolher. Correção de produção encaminha eventos ao controlador existente com coordenadas traduzidas. Não remover a asserção nem declarar esse teste aprovado até nova execução.
- Resultado final: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40155370964 — **79 testes executados, zero falhas/erros/ignorados**, compilação debug, integridade80/26, ZIP, zipalign e apksigner aprovados.
- Código/testes validados: bc640969f9d24ee6e32a03538a39b88de6dc5092, branch codex/final-consolidation, PR #4. Registro posterior altera somente documentação.
- APK diagnóstico: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40155370964/artifacts/12583364681 . SHA-256 c64b3f11b4a6b69ebe710ceb6fb1eda1dc9612117fc266c121433d93bcb8e55c.
- Relatórios: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40155370964/artifacts/12583639242 .
- Inspeção do APK: com.purplecharm.launcher, versionName0.7.1, versionCode8, debug. Certificado SHA-256 cfecf957e1fd08cec9e3cdbd7b9a0ba4fa12938cbaac438f67e8d2e07f95c4ce. Diferente dos dois APKs anteriores comparados: não atualizar por cima deles com este APK diagnóstico.
- YAML dos workflows validado localmente. SigningConfig release e novo workflow foram preparados, mas compilação com chave privada estável, execução com secrets e instalação/update compatível não foram realizados; dependem de provisionamento seguro da chave correta. Não declarar assinatura estável operacional.

## Pendente no POCO e para merge

Validar grade em todas as categorias, largura/fonte do proprietário, cabeçalho/nome/recorte/pesquisa/índice e rolagem; retirar membro por drag normal e modo explícito, cancelar, mover entre páginas e dock, pastas de1 e mais de6 membros, reiniciar e conferir ausência de perdas/duplicações. Confirmar menus internos e exclusões solicitadas. Verificar widgets/wallpaper/dock e máscara aprovados sem regressão.

Assinatura estável e update sobre instalação compatível dependem do keystore correto/secrets e teste real. Main continua preservada até confirmação desses testes e checks. Autorização de merge após validação completa já registrada; não pedir novamente nem forçar merge/ignorar falhas.

## Arquivos desta rodada

- app/src/main/java/com/purplecharm/launcher/MainActivity.kt: composição da gaveta, apresentação da pasta/root de drag e menus internos.
- DrawerAppGrid.kt, FolderSurface.kt, SmokeUi.kt: componentes internos compartilhados; WorkspaceDragLayout.kt: entrada de coordenadas traduzidas. Todos no mesmo pacote do launcher.
- WeatherLocationController.kt: usa o padrão visual, sem alterar localização/consulta.
- RetroImporterActivity.kt: removido; app/src/main/AndroidManifest.xml: registro removido.
- app/build.gradle.kts: versão e signingConfig opcional por ambiente.
- .github/workflows/android.yml e stable-apk.yml: APK diagnóstico, teste e assinatura estável separada/protegida.
- ConsolidationAndroidTest.kt, WorkspaceAndroidTest.kt, DrawerAppGridTest.kt: testes e adaptação das expectativas explicitamente alteradas pelo proprietário.
- docs/SIGNING_AND_UPDATES.md e este relatório: diagnóstico, resultados e pendências.
