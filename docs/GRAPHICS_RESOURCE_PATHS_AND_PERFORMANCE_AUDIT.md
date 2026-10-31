# Mapa de leitura de recursos e auditoria de fluidez — Purple Charm

## Caminhos confirmados
- `app/src/main/java/com/purplecharm/launcher/GraphicResourceCache.kt`: decodificação de PNG via `BitmapFactory`, amostragem `inSampleSize` por dimensões de destino e cache LRU de 16 MiB por instância.
- `app/src/main/java/com/purplecharm/launcher/MainActivity.kt`: `graphicCache` usa `GraphicResourceCache` para os widgets (`packagedImage`); cache de ícones LRU de 4 MiB e cache de rótulos com 128 entradas.
- `app/src/main/java/com/purplecharm/launcher/WeatherArtworkCatalog.kt`: catálogo lógico com 26 recursos e mapeamento slug → `R.drawable`; não depende de caminhos de arquivo em tempo de execução.
- `app/src/main/java/com/purplecharm/launcher/ExpandedWeatherActivity.kt`: tela expandida; agora usa `GraphicResourceCache.load` com tamanho de tela em vez de `setImageResource` sem amostragem.
- `app/src/main/res/drawable-nodpi/`: localização de recursos PNG meteorológicos identificada no projeto.

## Fluxo de leitura
`WeatherArtworkCatalog.select(...)` → `Art.resource` (ID Android) → `GraphicResourceCache.load(id, width, height)` → `BitmapDrawable` → `ImageView.setImageDrawable`.

No Android, recursos compilados são lidos por ID `R.drawable.*`, e não por caminho absoluto de PNG. O caminho acima é a referência correta para implementação.

## Diagnóstico preliminar, sem benchmarks
1. A home já tem cache de gráficos e ícones; não presumir que falta cache.
2. A tela expandida antes decodificava imagem com `setImageResource`, sem controle explícito de resolução. Alterada para usar o carregador existente, com dimensões de tela.
3. Cada instância de `GraphicResourceCache` tem cache próprio. A tela expandida não compartilha automaticamente a mesma instância de cache da home. Medir antes de criar cache global.
4. Investigar alocação e reconstrução de views ao arrastar ícones, navegação entre páginas, abertura de pastas e atualização de widgets.
5. Sprite sheets podem beneficiar animações com muitos quadros, mas não há evidência de ganho para PNGs estáticos já cacheados.
6. WebP lossless pode reduzir tamanho em disco, mas não garante menor uso de RAM nem mais FPS; testar hashes de pixels e desempenho antes de migrar.

## Validação obrigatória
- Compilar e executar todos os testes automatizados, inclusive os testes do diálogo meteorológico anterior.
- Confirmar integridade das 26 artes e 80 recursos pelo workflow.
- Medir tempo de troca de páginas, travamentos no arraste, memória alocada, cache hit/miss e decodificações antes/depois em aparelho POCO.
- Não converter ou alterar os PNGs originais, nem mexer na main, no dock ou no fluxo de publicação sem validação.

**Estado:** auditoria estática parcial; nenhuma medição de FPS, memória ou desempenho no dispositivo foi realizada.
