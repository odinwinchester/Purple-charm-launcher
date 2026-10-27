# Missão widgets/dock — auditoria inicial

PR #4 aberto, integração codex/final-consolidation f7622f4a346d84225eb799c9c0ee8bc27e24b0f6, main c07c643ce2edbdfbfa580d2461e771b96aa2f13c. Backup anterior da main preservado. Não integrar até os critérios e testes físicos solicitados serem concluídos. Não incluir a branch separada de Azathoth.

O proprietário aprovou no POCO a grade da gaveta, saída de pastas, menu fumê, remoção Retro0.4, aparência interna e persistência. Preservar os mecanismos e artes.

Achados: o dia/noite usa sufixo de símbolo de previsão (alguns não possuem sufixo) ou hora do aparelho; não calcula luz solar na cidade. Cache aceita série antiga indefinidamente e valores da arte são impressos/ilustrativos. Nenhum valor impresso pode ser corrigido sem alterar PNG, o que está proibido: dados reais continuam separados na tela de detalhes. Geometria deve ser validada em todas as artes e resoluções.

Índice A–Z está centralizado com faixa de letras limitada18dp; alterar apenas distribuição para altura útil inteira. Longpress em célula vazia não tem listener; enterEditMode somente reconstrói a home sem oferecer menu. Dock já tem edição transacional/modelo/persistência e alvos drag; faltam botões manuais e o centro atualmente retorna à home em vez de abrir a gaveta. Host de widgets Android ainda não existe. Assinatura estável já tem workflow/Gradle/documentação preparados, mas chave privada/secrets indisponíveis para provisionamento pela integração conectada.

Implementar de forma aditiva: cálculo solar offline por UTC/coordenadas, validade meteorológica explícita, editor acessível, controles do dock usando WorkspaceEdits, AppWidgetHost/Manager com consentimento/configuração e IDs persistidos, sem invadir bancos do POCO. Novos painéis reutilizam SmokeUi. Widgets devem ficar em viewport separado dos ícones/dock, com dimensões comunicadas ao provider, limites respeitados e opções explícitas de posicionamento.
