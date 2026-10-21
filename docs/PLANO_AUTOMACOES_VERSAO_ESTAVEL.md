# Purple Charm — automações para a próxima versão estável

Status: **planejado; ainda não implementado**. Escopo aprovado em 21/10/2026.

## 1. Automação do Discord (webhook)
- Reutilizar o secret de repositório `DISCORD_WEBHOOK_URL`, nunca armazenar a URL no código ou nos logs.
- Em compilação de teste: opcionalmente informar sucesso/falha e link para o artefato no canal apropriado, deixando explícito que é uma versão experimental.
- Em publicação de versão estável: enviar anúncio oficial, versão, notas de lançamento, link de download e requisitos de compatibilidade **somente após aprovação manual da publicação**.
- Manter a lista de aparelhos homologados baseada em testes reais. Não marcar compatibilidade automaticamente só por requisitos de hardware.
- Evitar menções automáticas e anúncios duplicados.

## 2. Atualização do aplicativo (APK estável)
- Canais separados: estável e beta; por padrão consultar apenas o canal estável.
- Verificar periodicamente novas versões publicadas como GitHub Releases estáveis (não pré-lançamentos), sem bloquear a interface do launcher.
- Exibir versão, novidades e confirmação; permitir baixar o APK e acompanhar progresso.
- Validar integridade (SHA-256 do APK publicado) e procedência/autenticidade, incluindo assinatura do pacote.
- Usar instalador do Android com consentimento do usuário; **não prometer instalação silenciosa**.
- Manter `applicationId` e assinatura de lançamento consistentes; configurar chave de release segura (não usar assinatura debug para distribuição estável).
- Preservar dados/configurações do usuário; tratar falhas de rede, ausência de permissão de instalação, espaço insuficiente e cancelamento.
- Não forçar instalação; manter launcher utilizável mesmo com falha de atualização.

## 3. Portões de lançamento
1. Corrigir travamentos e validar dock/gestos da v0.5.0 em aparelho real.
2. Criar assinatura de release persistente e pipeline de publicação seguro.
3. Implementar e testar verificador de atualização e publicação Discord em ambiente de teste.
4. Aprovar manualmente o primeiro APK estável.
5. Publicar release estável e então ativar as notificações oficiais e a verificação de atualização.

Este documento é um compromisso de escopo, **não** significa que a automação já esteja ativa.
