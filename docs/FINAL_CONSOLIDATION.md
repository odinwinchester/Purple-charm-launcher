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
