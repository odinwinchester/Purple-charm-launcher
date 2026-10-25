# Cosmic Horror — Azathoth — O Caos Primordial

Inclusão preparada exclusivamente em feat/cosmic-horror-azathoth, baseada em codex/final-consolidation f7622f4a346d84225eb799c9c0ee8bc27e24b0f6. Não integrar antes da consolidação atual. Main, PR #4, widgets meteorológicos e dock não foram alterados por esta inclusão.

## Recurso original

- Arquivo fornecido:1000135773.png; recurso:app/src/main/res/drawable/cosmic_horror_azathoth.png.
- PNG RGBA,941×1672,4.054.647 bytes. SHA-256:6622fd49e9d4456d4fabd94ae71ba4478575b8a05ec6eb810be89be67e3a46dd.
- Blob Git:43c8987ebaeb97803733dd32d22e44be6ed78358.
- Copiado sem reexportação, conversão, recorte, alteração de cor ou edição. Os80 gráficos anteriores e26 artes meteorológicas permanecem protegidos pelos manifests da base.

## Integração preparada

Primeiro item da coleção Cosmic Horror, após a coleção existente no mesmo seletor. Título Azathoth — O Caos Primordial; chave de preferência cosmic_azathoth. Seleção manual/persistência e destinos existentes reutilizados. Nenhuma preferência é alterada ao abrir a coleção; só ao aplicar explicitamente.

Thumbnail, prévia e home usam FIT_CENTER exclusivamente para esta nova arte; a imagem inteira conserva proporção vertical. Faixas pretas são esperadas se a tela tiver outra proporção. Wallpapers anteriores mantêm CENTER_CROP, opções e chaves anteriores. No destino de bloqueio, o enquadramento final ainda depende do Android/POCO, pelo fluxo existente; não prometer controle sobre recorte do sistema.

Cache existente de16 MiB, sem ampliação por densidade. Decodificação nativa ARGB8888 estimada em6.293.408 bytes (~6 MiB), sem contar objetos/texturas; amostragem do mecanismo existente quando aplicável. Não carregar cópias gigantes nem todas as artes em memória. Nenhuma biblioteca nova.

## Validação

Executar integridade do novo PNG e dos80 gráficos anteriores, testes de reconhecimento, seleção/persistência, moldura/proporção e cache, além do build Android. Resultado será registrado após Actions. Testes físicos de enquadramento, fontes, memória e destino de bloqueio continuam pendentes.

## Etapa posterior

Após a consolidação da versão atual, revisar/atualizar a base deste branch e integrar somente seu diff de wallpaper. Não sobrescrever eventuais correções posteriores da consolidação.

Versão horizontal para computador ainda não criada. Preparar depois uma derivação identificada separadamente, preservando este PNG mestre; manter entidade central, vórtice e ruínas, expandindo a composição em vez de esticar o retrato. Proporção/resolução final e resultado visual precisam ser definidos/validados na etapa própria. Publicação pelo bot Webhook também é posterior; nenhum envio ou alteração do bot foi feito nesta etapa.

## Resultado efetivamente verificado

- Código/testes90b634d7dbf2ee6001f3013d118dd888c53b5e15; Actions https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40156887681 aprovado: **82 testes, zero falhas/erros/ignorados**, integridade e build debug/ZIP/alinhamento/assinatura.
- Os79 testes da base continuam passando;3 novos cobrem recurso/cache, coleção/fluxo de aplicação e preferência após recriação, mantendo política visual dos wallpapers anteriores.
- Verificação adicional do APK em memória: res/drawable/cosmic_horror_azathoth.png tem exatamente o mesmo SHA-256 e bytes do PNG fornecido. Nenhuma conversão pelo empacotamento debug.
- APK diagnóstico do branch: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40156887681/artifacts/12585370903 . SHA-256 e7d6065c015562f4056f20e0c1aab2d30bac56e2b5be6dd92f772e29956d4ef2. Usa assinatura DEBUG-EPHEMERAL herdada; não é release consolidada nem indicação de atualizar a instalação do proprietário.
- Relatórios https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40156887681/artifacts/12585685387 . Registro posterior altera apenas esta documentação.
- As primeiras execuções detectaram duas suposições incorretas nos testes novos: aplicar pelo toque na prévia em vez do botão existente e exigir bitmap nativo mesmo após amostragem para tela menor. Os testes agora seguem o fluxo real e verificam limites/proporção do bitmap amostrado, mantendo hash integral do mestre.
- Main c07c643ce2edbdfbfa580d2461e771b96aa2f13c e branch do PR #4 f7622f4a346d84225eb799c9c0ee8bc27e24b0f6 conferidas sem alteração. Nenhum merge/novo PR/publicação pelo Webhook. Enquadramento e consumo no aparelho físico pendentes; integração só após consolidação atual.
