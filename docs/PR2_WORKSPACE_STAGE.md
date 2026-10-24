# PR #2 — inventário, área de trabalho, redimensionamento e menu

Base: `763f3890950de72d9602b5a8ae34cc6c0ee48ca7`. Main deve permanecer em `c07c643ce2edbdfbfa580d2461e771b96aa2f13c`. Sem autorização para merge.

## Sequência realizada
1. Inventário dos 20 PNGs com dimensões, SHA-256 completo e associações na base: `WEATHER_ARTWORK_INVENTORY.md` e `weather-artwork-inventory.json`. Verificador confere inventário, fixtures e bytes originais.
2. Widget e ícones separados em viewports limitados ao workspace. Na página principal, a área superior reserva 40% da altura disponível e os ícones usam os 60% restantes com rolagem independente. A reserva não muda com tamanho, clima, fase ou ocultação do widget. Nenhum atalho, célula salva, indicador ou dock é movido pelo widget. Indicadores/dock continuam fora do workspace. Demais páginas mantêm sua área de ícones.
3. Toque prolongado abre alças laterais fora da arte. Arraste encaixa a largura no número de colunas da grade; a altura é derivada da proporção original. O tamanho é limitado à área do widget e persistido em preferência própria. Arraste cancelado não salva. Tocar fora/Voltar encerra edição. Atualização meteorológica e gestos globais não interrompem redimensionamento. A ocupação vertical é derivada da proporção, com até três linhas virtuais na área reservada; não é redimensionamento vertical independente.
4. Investigação documentada em `WEATHER_MISSING_VARIATIONS.md`: oito luas de chuva/frio existem e já eram selecionadas automaticamente; dois PNGs solares dedicados (Dia Chuvoso/Dia Frio) não foram encontrados no repositório/histórico consultado. Nenhuma criação proposta ou executada. No automático, usa-se Dia Nublado como fallback de chuva/frio, sem apresentá-lo como novo arquivo.
5. Menu principal simplificado: localização, personalização opcional, restaurar automático, alças, ocultar, informações. Personalização expõe exatamente as 20 artes existentes, incluindo as oito luas antes ausentes do menu. Novas instalações começam em automático; preferências manuais anteriores não são apagadas.
6. Proposta para painel separado de previsão real em `WEATHER_REAL_DATA_PROPOSAL.md`; não implementada sem aprovação. A arte e seu conteúdo impresso permanecem intocados.

## Verificações nesta etapa
- Conferência local: 20/20 PNGs com os hashes originais; dimensões do inventário e fixtures corretas; catálogo com exatamente os 20 recursos existentes.
- `git diff --check`: sem erros antes do envio.
- Testes JVM: parsing, política de localização, geometria das 20 artes, limites dos viewports, encaixe das alças e catálogo. Resultado do Actions deve ser registrado após execução, sem confundir compilação com teste de interação no Android.

## Pendente de teste no aparelho
- Inspecionar todas as 20 artes em retrato/paisagem e com fontes/densidades diferentes, confirmando ausência de recorte, máscara, sobreposição e distorção.
- Redimensionar por ambas as alças, arrastar na diagonal, tentar ultrapassar limites, cancelar, tocar fora, usar Voltar, pausar/retomar e reiniciar o app; confirmar persistência.
- Confirmar que nenhuma atualização de clima altera a posição/tamanho da área de ícones, indicadores ou dock. A arte pode reduzir o tamanho efetivo para caber quando muda para uma proporção mais alta, sem alterar a preferência salva.
- Rolar ícones na área limitada, navegar horizontalmente, arrastar atalhos entre páginas, usar gaveta/dock/menu inferior e verificar que todos os atalhos continuam acessíveis. A reserva de 40% é uma escolha de layout desta etapa, a calibrar no aparelho; em telas curtas exige rolagem dos ícones.
- Conceder/negar/revogar localização, buscar cidade, testar offline e deslocamento real. Esses recursos anteriores não foram considerados validados em aparelho apenas por passarem nos testes JVM.
- Aprovar ou ajustar a proposta de apresentação dos dados reais antes de nova implementação visual. GIFs finais continuam pendentes e fora desta etapa.

## Evidência de compilação
- Primeira validação desta etapa: `568ae368024f7530dafa7c7da16d6a3a76e514ee`, Actions https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40064879266 .
- 16 testes executados, sem falhas, erros ou testes ignorados. Inventário/integridade, compilação e validação de assinatura/alinhamento passaram.
- Ajuste posterior evita salvar o frame anterior quando o dedo é solto antes do próximo layout da alça. A validação final desse ajuste será registrada após o Actions; interação por toque continua pendente no aparelho.
