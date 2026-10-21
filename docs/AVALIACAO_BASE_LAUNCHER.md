# Purple Charm Launcher — avaliação de base (2026-10-21)

## Objetivo
Criar um launcher Android moderno para distribuição gratuita a um grupo selecionado, preservando os recursos visuais originais do Purple Charm e permitindo desenvolver componentes próprios sem obrigação geral de abrir o código novo.

## Proteção do projeto
- A branch `backup/pre-launcher-migration-2026-10-21` conserva o estado da `main` no commit `fb7f587a63bf686c96e7f6363dbce1f09b67781a`.
- Não substituir o código atual nem importar repositórios inteiros sem análise e teste.

## Candidatos examinados

### Lawnchair
- Origem: https://github.com/LawnchairLauncher/lawnchair
- Base moderna e rica em recursos; licença e obrigações de componentes devem ser verificadas por versão.
- Uma base GPL pode obrigar a disponibilizar o código correspondente das modificações distribuídas aos destinatários. Não é a primeira opção para futuras funcionalidades proprietárias integradas.

### OpenLauncher
- Origem: https://github.com/OpenLauncherTeam/openlauncher
- Licença principal Apache-2.0 (ver LICENSE e licenças de dependências).
- O README oficial declara o projeto **descontinuado** e recomenda outra base mais recente. Portanto não importar como motor principal sem antes avaliar custo de atualização para Android moderno.
- Recursos listados: desktop paginado, dock, drag-and-drop, ocultação de apps, wallpaper rolável, pesquisa e icon packs.

## Critérios de seleção
1. Licença permissiva de todos os componentes reutilizados, com atribuições preservadas.
2. Compatibilidade com Android atual, incluindo regras de launcher padrão, widgets, notificações e gestos.
3. Build reproduzível no GitHub Actions e APK instalável em aparelho físico.
4. Facilidade de integrar assets originais Purple Charm e evoluir Classic/Crystal Glass.
5. Evitar dependências descontinuadas ou incompatíveis.

## Próximas etapas técnicas
1. Examinar outros projetos permissivos ativos e conferir suas dependências.
2. Selecionar um commit/tag estável e registrar as licenças.
3. Criar protótipo em branch separada; não alterar a main antes do teste.
4. Compilar, inspecionar manifest/minSdk/targetSdk e validar assinatura do APK.
5. Testar instalação e abertura no Android real antes de incorporar efeitos avançados.

> Status: apenas preparação e análise. Nenhum motor externo foi importado, compilado ou instalado ainda.
