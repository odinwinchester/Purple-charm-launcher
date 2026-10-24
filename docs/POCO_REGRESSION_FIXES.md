# Correções após teste real no POCO — v0.7.0

## Evidência recebida

O proprietário confirmou no POCO: mover ícones na mesma página, criar/abrir/renomear pastas e manter a organização ao trocar de página. Relatou falhas intermitentes entre páginas, pastas vazias, excesso de textos meteorológicos e espaçamento do índice da gaveta.

## Alterações

- Área estável recebe o arraste da home durante toda a sessão. Não depende de os filhos de uma página anteriormente oculta terem recebido `ACTION_DRAG_STARTED`. A troca usa as páginas existentes do ViewFlipper, sem reconstruir a hierarquia.
- Permanência de 350 ms na borda de 32 dp troca a página; repetição de 650 ms permite atravessar mais páginas. Sair da borda, soltar, cancelar ou remover a área cancela os callbacks. Limites de páginas são respeitados.
- Destino é a célula medida da página atualmente visível, considerando posição do viewport, widget, padding e rolagem. Centro de célula ocupada mantém criação/adição de pasta; bordas mantêm troca de posição. Área do widget não aceita drops.
- Mantidos sessão transacional, gravação atômica, rejeição de drop duplicado e de mudança concorrente; nenhuma posição é gravada durante a navegação por borda. Cancelar conserva o conteúdo anterior.
- Pastas que perdem seu último membro são removidas ao finalizar a operação e liberam a célula/dock. A limpeza não ocorre no meio de uma troca/reordenação, nem remove outras pastas vazias legadas. Remover atalho não desinstala o aplicativo.
- Home mostra somente a arte meteorológica; localização, previsão real, fonte, horário e explicações ficam em `Clima > Ver previsão real`. Cliente MET Norway, localização e seleção automática permanecem ativos.
- Índice da gaveta usa letras de até 18 dp, centralizadas, ajustadas à altura disponível. Faixa contínua de toque de 32 dp mapeia o dedo pela posição real das letras. Rolagem, pesquisa e categorias mantêm os mecanismos existentes.
- Nenhum PNG, wallpaper, implementação do dock ou navegação fora do arraste foi alterado. Main e branch da PR #2 preservadas; desenvolvimento na branch do PR #3.

## Validação

- Verificação local SHA-256/dimensões/blobs: 26/26 PNGs intactos.
- Regressões automatizadas adicionadas para passagem a página antes oculta, destino medido, commit único, reinício, temporizadores/bordas/cancelamento, região do widget, payload estranho, última entrada da pasta, troca/reordenação de pasta com um membro, pasta legada intocada, detalhe meteorológico e índice compacto com rolagem.
- Primeira execução: 58 testes, compilação e validação do APK aprovados (Actions 40109619780).
- Ampliação: 62/63 testes passaram em 40109795186. A asserção temporal de retorno por duas páginas esperava a segunda execução exatamente no limite de 1.000 ms; observou uma página de retorno nesse instante. O teste foi separado em uma asserção para cada troca, com 50 ms de tolerância de agendamento na segunda. Mantidos os requisitos de voltar à página original, não reconstruir a área e não alterar preferências. O encaminhamento pela raiz Android e todos os testes de drop/persistência passaram nessa execução. APK foi bloqueado após a falha.
- Código e testes validados: `c11d28ee994b3c741877381b43ed5099bed6e785`, branch `codex/integrate-six-weather-art`, PR #3 (base PR #2).
- GitHub Actions: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40109985804 — **63 testes, zero falhas/erros/ignorados**, integridade das 26 imagens, compilação Android, ZIP, zipalign e apksigner aprovados.
- APK de teste: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40109985804/artifacts/12566091706 . SHA-256 do APK: `c57544d1a63ee6738c4451a971d0d82b40121cd900851223a6b81c909447b6d0`.
- Relatórios dos testes: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40109985804/artifacts/12565339506 .
- O registro posterior altera somente documentação, sem modificar o código do APK validado. Nenhum merge realizado. Main e PR #2 permanecem preservadas.
- A aprovação automática não confirma fluidez, latência ou ausência de intermitência no POCO. Essa confirmação continua pendente.

## Pendente no POCO

Não foi executado teste físico desta correção. Confirmar movimentos repetidos nos dois sentidos, entre páginas cheias e vazias, retorno ao centro antes de soltar, permanência longa na borda e cancelamento por Voltar/saída. Verificar ausência de duplicação/perda, posição após reiniciar, arraste ao dock e organização de pastas.

Confirmar remoção da última entrada por remover/extrair/mover para outra pasta, preservando as demais. Conferir home sem legendas, acesso à previsão/localização, proporções das 26 artes e alças. Validar letras/touch/rolagem/pesquisa/categorias em retrato e paisagem e com tamanho de fonte do proprietário. A latência e intermitência do arraste só poderão ser declaradas resolvidas após essa validação física.
