# Prioridade crítica — arraste e pastas

Base: `9536cc2fa48f6cca1f2c9cf3e293b652ec3d0303`, branch `codex/weather-widgets`, PR #2.

## Ordem do plano
1. **Crítico: investigar e corrigir arraste, reposicionamento e pastas.** Esta etapa vem antes de qualquer novo trabalho de redimensionamento dos widgets.
2. Validar interações e persistência no aparelho; corrigir regressões encontradas.
3. Retomar ajustes de redimensionamento somente depois da prioridade crítica.
4. Continuam pendentes: proposta de painel de dados reais (aprovação necessária), GIFs finais e calibração visual dos widgets. Nenhuma nova alteração desses recursos nesta etapa.

## Investigação do código base
- O app já usava `startDragAndDrop`, posições por célula, quatro slots de dock, persistência conjunta e atualização de views adiada até terminar o arraste. Esses mecanismos foram reaproveitados.
- Não havia modelo, armazenamento nem interface de pastas. Soltar em outro aplicativo apenas trocava/deslocava atalhos.
- Alvos aceitavam MIME genérico de texto, sem sessão local validada; várias rotas retornavam sucesso mesmo sem salvar. Células sem aplicativo não tinham alvo próprio.
- A movimentação para uma página cheia era recusada antes de considerar troca com a posição de origem. O dock não tinha a mesma proteção de confirmação única em todos os caminhos.
- A gaveta abria ações no toque prolongado em vez de iniciar arraste.
- A causa física do relato “não arrasta” ainda requer reprodução no aparelho. Não atribuir sua resolução somente à compilação ou aos testes simulados.

## Implementação
- Toque prolongado usa arraste nativo, com estado de sessão preparado antes dos callbacks síncronos. Falha ao iniciar não grava nada. Na gaveta, a Home é preparada e o arraste começa a partir de uma view anexada à janela, mantendo a sombra do aplicativo.
- MIME privado e identidade de sessão local filtram eventos externos/obsoletos. A origem não é removida ao iniciar; cancelar não grava. Cada sessão aceita no máximo uma confirmação persistida. Mudança concorrente de layout invalida a operação.
- Células vazias e ocupadas são alvos. Centro de um aplicativo cria pasta; centro de pasta adiciona aplicativo. Bordas da célula permitem reposicionar/trocar sem criar pasta. Não há pastas aninhadas.
- Motor de operações imutável preserva os demais itens, incluindo trocas em páginas cheias. O estado anterior só é substituído após validar a proposta.
- Os mesmos campos de páginas/células/dock são usados. Metadados de pastas versionados em JSON entram na mesma gravação. IDs estáveis, nomes e ordem dos membros persistem. Preferências de tema, wallpaper e clima não são gravadas por esse armazenamento.
- Em falha de gravação, restauração do snapshot anterior inclui pastas, pois o Android pode alterar o mapa em memória mesmo quando a gravação em disco falha. Falha de I/O não é apresentada como sucesso.
- Pasta: toque abre; toque prolongado move; título/“Renomear” altera nome; “Adicionar” escolhe aplicativos; arraste sobre membro reorganiza; “Organizar” oferece movimentação/remoção/reordenação. A área “Solte aqui para mover à tela inicial” extrai o membro para célula livre, criando página apenas se necessário e houver capacidade. Extração sem espaço é recusada sem remover a origem.
- Remover da pasta remove somente o atalho; não desinstala aplicativo. Pastas vazias continuam válidas. Remover uma pasta inteira como atalho é recusado para não descartar seus aplicativos silenciosamente.
- Interface só é reconstruída depois do ciclo nativo de drag; pasta aberta é recarregada após uma edição confirmada. Cancelamento mantém o diálogo/itens.

## Preservação
- Wallpaper e dock visual existentes mantidos. O motor de posições do dock recebe suporte ao novo tipo pasta sem alterar seus quatro slots.
- Os 20 PNGs e o verificador SHA-256 não foram alterados nesta etapa. Widgets meteorológicos e alças permanecem como na base.
- Layouts legados que já repetiam um aplicativo entre páginas não são automaticamente apagados/migrados ao carregar. Ao mover um item, suas instâncias anteriores são removidas como no mecanismo antigo para evitar duplicação na operação.

## Validação prevista
- Testes JVM de criação/adição/remoção/reordenação/renomeação/movimentação, páginas cheias, extração, trocas com dock, cancelamento, confirmação única, conflito concorrente, codec e invariantes em operações repetidas.
- Testes Robolectric com SharedPreferences real simulado, falha de commit injetada, eventos de drag em células reais da Activity, rejeição de payload externo, cancelamento e recriação.
- GitHub Actions: testes e resultado numérico, integridade dos 20 PNGs, compilação, alinhamento e assinatura do APK. Resultados efetivos serão registrados após execução.

## Pendente no aparelho
- Verificar início por toque prolongado na Home/gaveta/dock/pasta, sombra e resposta visual; testar Android/POCO e densidade/fonte reais.
- Arrastar para células vazias, bordas/centro de ícones, pastas existentes, dock e páginas laterais; testar rolagem durante organização.
- Criar, preencher, renomear, reorganizar, extrair e mover pastas; encerrar/reabrir e reiniciar o launcher; conferir nomes, conteúdo, ordem e posições.
- Cancelar fora de alvos, pressionar Voltar, pausar o app, iniciar gesto que falha e soltar repetidamente; confirmar ausência de perda/duplicação e recuperação da interface.
- Testar limites de páginas, pasta vazia e aplicativo removido do Android. Simulação não substitui esse aceite físico.
- Main deve permanecer em `c07c643ce2edbdfbfa580d2461e771b96aa2f13c`. Não fazer merge sem autorização.
