# Consolidação — auditoria anterior às modificações

Base main: c07c643ce2edbdfbfa580d2461e771b96aa2f13c. Base escolhida: codex/integrate-six-weather-art, d549c21a5962e03a5994c95196494c5cb5eea7d2 (PR #3, sobre PR #2). Nova branch: codex/final-consolidation. Backup remoto da main: backup/pre-final-consolidation-main. Nenhum merge na etapa de auditoria.

## Comparação dos ramos

- PR #2 aberta: 3b2bcf8, arraste/pastas persistentes, localização e 20 artes.
- PR #3 aberta: d549c21, contém PR #2, seis novas artes e correções posteriores ao teste real no POCO; 63 testes previamente aprovados.
- feat/novas-artes-meteorologicas: 5b1474c, fonte dos seis PNGs já incorporados byte a byte.
- fix/workspace-drag-stability e feature/permissive-launcher-evaluation: ancestrais da base escolhida; não reimplementar.
- fix/widget-refresh-stability-20261022: ramo antigo divergente; comportamento de atualização parcial já existe na base mais nova. Não sobrescrever com código antigo.
- feature/retro-04-integrado: PR #1 já integrada à main. Importador legado é ferramenta de recursos, não arquitetura do launcher.

## Já implementado / pendências observadas

- Arraste entre páginas com superfície estável, criação/renomeação/reordenação/persistência de pastas, limpeza da pasta que perde último membro: preservar. Aceite físico da última correção ainda pendente.
- Pastas: quatro colunas, viewport fixo 230 dp e painel opaco; ajustar apresentação. Arraste para extrair existe por alvo no diálogo; acrescentar saída direta segura sem perder a origem.
- Menu de opções: três comandos existentes em AlertDialog padrão; alterar apresentação preservando comandos.
- Gaveta: índice compacto já existe. Grades por letra adicionam 24 dp de padding vertical e linhas não uniformes; reduzir sem mudar agrupamento/ordenação.
- Ícones: originais personalizados e moldura original já usados. fallback ghost pode duplicar moldura na tile. Nenhum novo desenho é necessário. Apps sem correspondência dependem da lista instalada no POCO, indisponível nesta sessão.
- Bateria: nenhum indicador funcional redundante encontrado. Pesquisa: uma implementação com entradas por gesto/barra; não confundir atalhos com funções duplicadas. Não remover relógio/calendário/artes impressas.
- Olho de Crystal: wallpaper na galeria e atalho para bloqueio, não widget implementado. Unificar acesso ao wallpaper; widget exato é pendência de especificação/arte aprovada, sem criação inventada.
- Menu inferior contém Tema e GO config. apontando ao mesmo settingsMenu: duplicação comprovada; unificar como Configurações, mantendo comandos do menu.
- Clima: catálogo26, cache MET, manual/automático, eclipses manuais, FIT_CENTER e persistência já implementados. Atualização de minuto recria o componente mesmo sem mudança visual; corrigir comparação de estado.
- Gráficos: wallpaper default é carregado antes da preferência; cache limitado/decodificação sem ampliação de densidade são candidatos. Sem WebP/GIF/conversão de artes.

## Critério de integração

Executar testes e build no Actions, conferir todas as artes da base e publicar APK de teste. Novos visuais e transferência de pasta para home exigem validação física no POCO antes de declarar conclusão/estabilidade e integrar na main conforme a condição de validação da missão. Não simular esse aceite.

## Ajustes implementados na integração

- Menu fumê sem blur: painel ARGB(218,14,9,20), contorno violeta de 1 dp, destaque pressionado/focado, três comandos preservados. Sem fundo adicional opaco/dim do sistema.
- Pasta com fundo transparente e uma borda violeta; três colunas e viewport de uma ou duas linhas de 88 dp. Mais de seis itens continuam na mesma pasta e podem ser acessados rolando. Adicionar/renomear/organizar/extrair preservados.
- Para manter o arraste de reordenação dentro da pasta: `Arrastar para início` arma o modo; depois segurar um membro recolhe a janela e inicia pelo view da home ainda anexado. Não há gravação até drop válido; falha/cancelamento preserva os membros. O alvo de extração anterior continua aceitando drop.
- Gaveta mantém agrupamento/ordenação/categorias/pesquisa e índice de até18 dp; remove padding de seção e usa linhas uniformes de88 dp com nomes de duas linhas. Rolagem não reconstrói os itens.
- Personalizados originais mantidos. Uma única moldura original por tile; evitada composição de fallback duplicada. Miniaturas da pasta podem usar o fallback original quando configurado.
- Menu inferior unifica Tema/GO config. em Configurações. Olho de Crystal fica na galeria de papéis de parede, que mantém o destino de bloqueio por toque prolongado; nenhuma arte apagada. Não há widget Olho implementado para eliminar ou recriar.
- Cache de bitmaps empacotados limitado a16 MiB; cache de ícones limitado a4 MiB contabilizados/estimados e nomes a128 entradas. Drawables separados compartilham pixels. Evicção não recicla bitmap exibido. Limpeza em pressão de memória e destruição.
- Wallpapers decodificados sem ampliação por densidade e com amostragem somente quando o arquivo excede o tamanho de exibição. PNGs do clima mantêm dimensões nativas/proporções. Fundos intermediários desnecessários evitados.
- Widget é reutilizado quando arte/colunas/span não mudaram. Atualização de dados MET/localização continua; detalhes reais consultados sob demanda. Nenhuma reconstrução da área de trabalho durante essa atualização.
- Sem biblioteca nova, WebP, PNG/GIF alterado ou animação solar ativada. Pipeline assíncrono de fotos externas e medição real de memória/FPS não foram introduzidos sem validação específica.

## Pendências explícitas

- Lista dos aplicativos sem ícone gótico no POCO: depende dos aplicativos instalados; preservar fallback/moldura existente, sem inventar novas artes.
- Widget Olho de Crystal: não existe no código auditado. Falta especificação/arte exata aprovada para um widget, distinta do wallpaper existente. Nenhum widget novo foi inventado.
- Quatro animações solares: classe antiga de sobreposição não usada não é GIF aprovado; não ativada para evitar mudança visual não validada. Produção/integração das animações exatas continua pendente.
- Eclipses continuam manuais; automatização depende de fonte/cálculo confiável e visibilidade local.
- Aceite físico do POCO: menu fumê e três destinos, pasta transparente compacta, rolagem de pasta com mais de seis itens, reordenação, transferência para home/dock/páginas, cancelamento, última entrada, reinício, nomes/índice/categorias/pesquisa,26 artes e tamanhos. Verificar legibilidade com fonte/densidade do proprietário e atualização real/offline de MET/localização. Memória/FPS/vazamentos físicos ainda não medidos.
