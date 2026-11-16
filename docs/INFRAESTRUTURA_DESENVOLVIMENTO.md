# Infraestrutura de desenvolvimento Purple Charm

**Auditoria:** 15/11/2026  
**Branch:** `development/v12-continuacao`  
**Escopo:** sem mudanças em `main`, assinatura oficial, keystore, versão de release ou publicação definitiva.

## Estado verificado

| Componente | Estado |
|---|---|
| Seis agentes Codex | Os seis TOMLs de `.codex/agents/` passaram pelo parser TOML e têm `name`, `description`, `sandbox_mode` e `developer_instructions`. As instruções seguem os limites de `AGENTS.md`. |
| Execução real dos agentes | Pendente. A configuração foi validada estruturalmente; ainda é necessário abrir o checkout em Codex/T3 e confirmar que cada perfil é reconhecido e chamado. |
| GitHub Actions | O run #433, no commit `c5e6178a1a6278e98789c5c093773baac6ae9af3`, passou na branch. |
| APK debug | Run #435 passou: compilou `app-debug.apk`, publicou o artefato `Purple-Charm-Debug-APK` (49 MB) e concluiu as verificações. O job de APK oficial assinado ficou skipped na branch de desenvolvimento. |
| APK oficial | O job de release assinado permanece condicionado a push em `main`; não foi editado. |
| Teste em dispositivo | Pendente. A CI atual não executa Firebase Test Lab nem instala o APK em aparelho. |
| T3 Code / REA | Auditados; não instalados neste ambiente de trabalho temporário. |
| Servidor Oracle | Não é necessário para iniciar. Nenhuma instância foi criada. |

## Agentes especialistas

Os seis perfis existentes são:

- `architect`: planejamento Android, somente leitura por instrução.
- `build-resolver`: Gradle/APK, diagnóstico local autorizado.
- `kotlin-android`: implementação delimitada pelo coordenador.
- `performance-reviewer`: análise de desempenho com medições.
- `security-reviewer`: auditoria sem acessar segredos.
- `test-reviewer`: testes e regressões, sem editar implementação.

**Limite de segurança do Codex:** o `sandbox_mode` de um TOML personalizado não cria isolamento próprio. O agente herda a política de permissões da sessão principal. Para qualquer revisão que deva ser efetivamente somente leitura, iniciar a sessão principal em modo somente leitura; para implementar, iniciar outra sessão com permissão de escrita e escopo explícito. As instruções “não editar” são orientação comportamental, não uma barreira técnica.

## Fluxo proposto: ChatGPT, T3 Code, Codex e especialistas

O T3 Code roda na máquina que contém o checkout. O celular e o ChatGPT são clientes remotos. O caminho compatível documentado é:

1. Instalar T3 Code no computador/servidor escolhido e abrir este repositório na branch `development/v12-continuacao`.
2. Conectar Codex no T3 Code usando a conta ChatGPT existente. Isso usa a assinatura do provedor já existente; não requer uma chave OpenAI adicional.
3. Ativar T3 Connect no ambiente do T3 Code.
4. Adicionar o endereço T3 Connect terminado em `/mcp` como app MCP personalizado no ChatGPT, com OAuth, e aprovar o acesso iniciado pelo proprietário.
5. Em uma tarefa de teste, pedir ao ChatGPT para iniciar uma thread Codex que leia `AGENTS.md`, identifique os seis perfis e use um revisor; conferir no T3 Code o histórico e o resultado antes de ampliar permissões.

O ChatGPT conectado como cliente externo pode ler e iniciar threads, mas não pode acionar diretamente as ferramentas de subagentes internas do T3. A delegação aos perfis deve acontecer na thread Codex executada dentro do T3. A documentação do T3 também não oferece notificação MCP automática de conclusão; pode ser necessário consultar a thread até terminar.

T3 Code é MIT e gratuito; T3 Connect é informado como gratuito no momento desta auditoria, com limite atual publicado de três máquinas por conta. Limites e disponibilidade podem mudar. T3 Code está em estágio inicial. O código permanece na máquina hospedeira, enquanto prompts/código enviados ao modelo seguem para o provedor escolhido.

**Autorizações ainda necessárias:** instalar T3 no computador persistente do proprietário; entrar na conta ChatGPT para compartilhar a assinatura com o Codex no T3; ativar T3 Connect; criar a conexão MCP no ChatGPT e aprovar OAuth para o endereço do ambiente. Não compartilhar senha, token, keystore ou segredo de assinatura no chat, no MCP ou nos logs.

## REA e dependências Android

A fonte identificada é o projeto upstream [morluto/rea](https://github.com/morluto/rea), pacote npm `rea-agents`, MIT. Na auditoria, o pacote declarou versão 6.9.0 e suporte a Node.js 22.23.4+, 24.22.1+ ou 26.12.1+. O ambiente temporário tem Node 24.22.1 e Java 21, porém não tem JADX nem Android SDK/ADB; portanto não foi possível analisar um APK.

Para análise estática de APKs Android, REA requer um JDK completo e um JAR headless do JADX fornecido pelo operador. Não requer emulador para essa análise. A instalação de Hopper é opcional e usa licença própria; não faz parte da configuração sem custos. REA não é sandbox: analisar um APK significa processar um arquivo externo com as permissões do usuário local.

Instalação recomendada apenas no host persistente, após o proprietário conferir a versão e a prévia de alterações do instalador:

```sh
npx -y rea-agents@6.9.0 setup
npx -y rea-agents@6.9.0 doctor
```

No setup, selecionar somente Codex e revisar os arquivos que serão adicionados antes de aprovar. Instalar/configurar JADX à parte e validar o diagnóstico de Android antes de abrir um APK. Usar artefato debug ou APK cuja origem seja conhecida; nunca entregar a REA a keystore, senha ou APK de terceiros sem autorização.

## GitHub e testes de APK

O workflow `.github/workflows/android.yml`:

- valida recursos meteorológicos;
- roda `:app:testDebugUnitTest` (testes JVM/Robolectric) e exige ao menos 107 testes sem falhas, erros ou skips;
- publica relatórios e, a partir desta alteração, um APK debug para download como artefato;
- só roda o job de APK oficial assinado em push para `main`.

O repositório contém `UpgradeProbe`, um runner de instrumentação voltado à persistência, mas o workflow atual não o executa. Ainda não há fluxo UI Espresso/UIAutomator configurado. O APK debug gerado é um build de teste e não é uma versão definitiva nem usa a assinatura oficial.

### Firebase Test Lab

O Test Lab ainda pode ser avaliado em modo sem custo usando o plano Spark e a CLI: a quota publicada é de até 10 execuções virtuais e 5 físicas por projeto por dia. O console pode exigir o plano Blaze; **não habilitar billing nem migrar para Blaze** dentro desta missão. O serviço será encerrado em 30/09/2027. O substituto, Developer Device Platform, está em prévia e requer billing, então não atende à condição de custo zero.

Para habilitar um teste depois, o proprietário precisa criar ou escolher um projeto Firebase/Google Cloud, autorizar as APIs de teste e configurar autenticação do GitHub Actions por OIDC/Workload Identity Federation com IAM mínimo. O fluxo deve usar um Robo test inicial de um único aparelho e interromper se a conta não estiver em Spark ou se o serviço indicar custo. Nenhum projeto, identificador, credencial ou autorização Google está disponível nesta sessão.

## Oracle Cloud Always Free

Não é necessária para começar. O GitHub Actions já compila em `ubuntu-24.04` e o repositório é público. O T3 Code pode rodar no computador do proprietário com o projeto aberto; manter esse computador ligado e usar T3 Connect evita um servidor permanente.

OCI pode ser reavaliada somente se o proprietário quiser um host disponível 24/7 mesmo com o computador desligado. A oferta Always Free anuncia até 2 OCPU e 12 GB para A1 na região inicial, sujeita a capacidade; instâncias ociosas também podem ser recuperadas. Provisionamento exige conta/SSH e validação da stack no processador ARM. Nenhuma conta ou máquina foi criada.

## Próxima validação

1. No host persistente, testar abertura da branch no T3 Code e chamada real dos seis perfis; executar revisão com sessão em modo somente leitura.
2. Após autorização Google do proprietário, rodar primeiro um Robo test no Firebase Spark e guardar a matriz/logs como prova.
3. Somente depois avaliar se é necessário um host 24/7; não provisionar OCI para substituir os runners gratuitos.

## Referências externas

- [Codex: subagents, permissões herdadas e arquivos TOML](https://learn.chatgpt.com/docs/agent-configuration/subagents)
- [T3 Code: instalar](https://github.com/pingdotgg/t3code/blob/main/docs/user/install.md)
- [T3 Code: agentes externos e OAuth MCP](https://github.com/pingdotgg/t3code/blob/main/docs/user/outside-agents.md)
- [T3 Code: FAQ e limites atuais](https://github.com/pingdotgg/t3code/blob/main/docs/user/faq.md)
- [REA: upstream](https://github.com/morluto/rea)
- [REA: segurança](https://github.com/morluto/rea/security)
- [Firebase Test Lab: cotas e preços](https://firebase.google.com/docs/test-lab/usage-quotas-pricing)
- [Firebase Test Lab: CLI](https://firebase.google.com/docs/test-lab/android/command-line)
- [Firebase Test Lab: migração e encerramento](https://firebase.google.com/docs/test-lab/migrate)
- [Oracle Cloud: recursos Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
