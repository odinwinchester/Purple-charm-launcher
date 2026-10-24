# Integração das seis novas artes meteorológicas

Base de desenvolvimento: `codex/weather-widgets` em `3b2bcf8e0ca4b546d472fa7232fee951a951e9b2`. Origem das seis imagens: `feat/novas-artes-meteorologicas` em `5b1474c03b59a0ccaf0486a1ad42576d2048e9ff`.

Estratégia: branch própria `codex/integrate-six-weather-art`, baseada integralmente na PR #2. Copiados apenas os seis blobs PNG, byte a byte; nenhum código antigo da branch de artes foi mesclado por cima das correções. PR de integração terá como base `codex/weather-widgets`, não `main`. Nenhuma branch existente foi sobrescrita e não foi feito merge.

## Verificação dos arquivos

Todos os seis arquivos abriram e passaram pela verificação de decodificação/estrutura PNG. São RGB, sem canal alfa, com suas bordas e fundos impressos preservados. Não houve reexportação, recorte, otimização destrutiva ou redesenho.

| Arquivo | Dimensões | Bytes | SHA-256 |
| --- | --- | --- | --- |
| sol_dia_chuvoso.png | 867 × 453 | 696823 | `2823f7a3ca116d19a15eda0c8846c8aef5d9d9efb68397f7c50fc42618ee73e5` |
| sol_dia_frio.png | 867 × 453 | 703226 | `f54c33c37926efa6dd5ba4042e866f669542ae760c20e5dcaaa3eb2cd308cee5` |
| lua_noite_chuvosa.png | 1942 × 396 | 1333375 | `25f1845f92c3a3db6ddd6341e17d265b7add4bdf77409b984f47c4454b93af77` |
| lua_noite_fria.png | 1942 × 413 | 1349297 | `10e5b8666aec19b2998c9dfd47d02b9a3ae82f3d0b01d470bccf8a17c924b965` |
| eclipse_solar_frio.png | 867 × 454 | 650530 | `19f018616756262a7a28c84552ad044ec9c3a5a8224b416877a0683cbd98ce33` |
| eclipse_lunar_limpo.png | 867 × 454 | 633453 | `adb3bc52d0a80c68971834925ce37e7e759a082a01c9c15c9dd4992177638a48` |

Inventário estruturado e identificação dos blobs de origem: `weather-additional-artwork-inventory.json`. Os manifests dos 20 originais não foram reescritos. O verificador confere os 26 arquivos, os seis blobs de origem, dimensões, bytes e catálogo.

## Comportamento implementado

| Arte | Classificação e seleção |
| --- | --- |
| sol_dia_chuvoso.png | Solar meteorológico: Automático diurno quando o grupo da previsão MET é chuva/trovoada; manual também disponível |
| sol_dia_frio.png | Solar meteorológico: Automático diurno quando há neve/neve e chuva ou temperatura <= 12 °C; prioridade sobre chuva quando frio; manual disponível |
| lua_noite_chuvosa.png | Lunar alternativo panorâmico: Somente manual nesta etapa; não substitui o mapeamento automático das quatro fases |
| lua_noite_fria.png | Lunar alternativo panorâmico: Somente manual nesta etapa; não substitui o mapeamento automático das quatro fases |
| eclipse_solar_frio.png | Evento astronômico ilustrativo: Somente manual; não confirma ocorrência ou visibilidade de eclipse |
| eclipse_lunar_limpo.png | Evento astronômico ilustrativo: Somente manual; não confirma ocorrência ou visibilidade de eclipse |

- As seis artes são opções do componente meteorológico já existente. Não foram criados seis AppWidgetProviders, serviços ou widgets Android independentes.
- Catálogo com 26 IDs distintos. Os 20 IDs/recursos e os 16 mapeamentos de fase/condição anteriores continuam preservados e na mesma ordem.
- As condições vêm da previsão MET existente. Frio/neve mantém a precedência já usada pelo componente, inclusive no caso de chuva com temperatura <= 12 °C. Não há dados válidos: não inventar condição nem inferir eclipse.
- Lua Nova, Crescente, Cheia e Minguante continuam sendo calculadas/selecionadas pelo mecanismo anterior. As novas luas são alternativas manuais sem identificação confiável de quatro fases; a fase do componente aparece separada da arte.
- Eclipses têm classificação astronômica explícita, nunca meteorológica. Somente escolha manual, acompanhada de aviso fora da imagem: evento/visibilidade não confirmados. Não há mecanismo automático de eclipse nesta etapa.
- A arte original é sempre exibida inteira com FIT_CENTER e borda externa existente. Valores impressos continuam ilustrativos; a previsão MET e os avisos são separados, sem sobreposição à imagem.
- Os dois banners lunares têm proporção próxima de 4,7–4,9:1. Abrem usando todas as colunas disponíveis e permitem redimensionamento próprio persistido. A preferência global anterior não é sobrescrita. Em telefone estreito, textos impressos de um banner de 1942 px podem continuar pequenos; não foram ampliados por recorte nem escondidos.
- Áreas de widget/ícones, navegação, wallpaper, dock, arraste e pastas são reutilizados. Nenhuma classe desses mecanismos foi substituída.

## Validação antes de entrega

- Local: seis PNGs verificados com decodificador, hashes SHA-256, blobs Git e dimensões; 20 originais preservados; verificador de 26 arquivos passou; `git diff --check` sem erros.
- Os 39 testes anteriores permanecem, com atualização do teste de tamanho do catálogo para 26 e conferência explícita dos mesmos 20 originais.
- Novos testes: reconhecimento e classificação dos seis recursos, seleção solar por condição, preservação das quatro fases, impossibilidade de eclipse automático, manual/legado, geometria das seis imagens, mensagens de eclipse/fase, decodificação Android dos 26 recursos, previsão MET em cache, menu manual real, restauração do automático, persistência/recriação, alça panorâmica e não alteração de posições/pastas/dock/wallpaper.
- Resultado do Actions, commit validado e APK serão registrados após a execução. Não tratar esta previsão de testes como resultado já concluído.

## Pendências no aparelho e fora do escopo

- Validar visualmente as 26 artes, em especial a leitura dos dois banners lunares, margens/molduras, fontes, densidades, retrato/paisagem e todos os tamanhos permitidos.
- Validar os sóis automáticos com atualização real do MET, cidade/localização e offline/cache antigo. Os testes determinísticos usam cache controlado, não observações meteorológicas ao vivo.
- Confirmar menu, persistência, alças, navegação, dock, arraste e gestão de pastas no aparelho físico. Testes simulados não substituem esse aceite.
- Automação de eclipse pendente: exige fonte/cálculo confiável de ocorrência, horário e visibilidade por localização e validação específica. Não usar fase lunar aproximada ou temperatura como prova de eclipse.
- Novos GIFs e novo painel de dados reais não foram implementados nesta integração. Os valores impressos nos PNGs não foram alterados.
- Não fazer merge na `main` sem autorização.

## Primeira execução e correção localizada
- Actions https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40075438536 : o código compilou e 49 dos 50 testes passaram; a integridade dos 26 PNGs passou.
- O teste de eventos reais simulados da alça detectou `esperado 3, obtido 1`: na revisão anterior, `ACTION_UP` consultava `width/height` do TextView da alça (24 dp), não do contêiner. Isso fazia a preferência diminuir à menor largura.
- Correção pontual: qualificar `this@WeatherResizeLayout.width/height`. O mecanismo existente não foi reimplementado. Teste adicional exercita também uma arte original; mínimo atualizado para 51 testes. Aguardar resultado da revisão correspondente.
