# Widgets meteorológicos — registro verificável

Base: `c07c643ce2edbdfbfa580d2461e771b96aa2f13c` (`main`).

## Implementado nesta etapa
- Preservação dos 20 PNGs, comprovada por SHA-256; conferência repetida no Actions.
- MET Norway com consulta ao retomar o launcher e verificação a cada minuto enquanto visível. Rede respeita Expires, If-Modified-Since e espera após falhas.
- Cache da série temporal, seleção da hora corrente, fallback de resumo 1h/6h/12h e descarte de resposta de local anterior.
- Previsão real em área separada, com horário, unidades, crédito e indicação de dados antigos. Valores impressos nos PNGs identificados como ilustrativos.
- Atualização restrita ao widget, sem reconstruir páginas, atalhos, dock ou papel de parede.
- Seleção corrigida: neve com trovoadas continua na categoria neve; nevoeiro usa arte nublada; poucas nuvens não força noite nublada.
- Quatro efeitos nativos opcionais de luz, um para cada sol; estáticos por padrão, respeitam animações desativadas no Android. Não substituem os GIFs solicitados anteriormente.

## Validação
- Conferência local de 20 hashes: passou.
- `git diff --check`: passou antes do envio.
- Testes unitários e compilação: passaram para `ce8f9b3bf3e1daa077476126a40fa0ce8a4034de`.
- Actions: https://github.com/odinwinchester/Purple-charm-launcher/actions/runs/40051701832 — todas as etapas concluídas com success.
- APK debug gerado, ZIP íntegro, alinhamento e assinatura v2 verificados no Actions. SHA-256: `458ec810104329c6bac0379fbdfa4c75ab075f317f41843288c84196d3c96b28`.
- Consulta direta ao MET Norway: HTTP 200, série com 93 instantes e resumos 1h/6h/12h. Isso valida a disponibilidade e o formato da API, não a execução em aparelho.
- Aparelho Android/POCO: ainda não testado.

## Pendências explícitas
- GIFs finais com piscada/sorriso, rotação dos raios e movimentos específicos das artes: não produzidos nesta etapa. Os efeitos de luz são preliminares.
- Substituir números impressos dentro da moldura exigiria remover parte da imagem original; isso não foi feito. A área dinâmica separada preserva integralmente as artes, mas não equivale a um template em camadas.
- Validar visualmente no Android os três tamanhos, ciclo sol/lua, localização, rede/offline e efeitos.
- Não publicar como versão estável validada no aparelho.

# Etapa 2 — localização e apresentação (PR #2)

Base desta etapa: `255db5016d72a21835e0af4518a44ef52b7bb332` na branch `codex/weather-widgets`. Não fazer merge sem autorização.

## Implementado
- Menu com localização automática e busca por nome de cidade, sem exigir digitação de latitude/longitude. Coordenadas anteriores continuam utilizáveis.
- Permissão Android de localização aproximada solicitada somente ao tocar em “Usar minha localização”. Não solicita localização precisa nem acesso em segundo plano.
- Localização de rede apenas com launcher em primeiro plano e widget visível: intervalo mínimo de 5 minutos e deslocamento mínimo de 2 km. Última posição aceita somente até 30 minutos e precisão até 10 km. Pausa remove o listener.
- Mudanças de posição aceitas invalidam a previsão anterior e atualizam coordenadas/cidade. Pequenas oscilações não invalidam o cache. A localização aproximada não identifica exatamente os limites municipais.
- Geocoder Android: busca por cidade com lista de resultados (cidade/estado/país) e escolha explícita. API 33+ assíncrona; APIs 26–32 fora da thread de interface. Falhas, ausência do serviço, negação de permissão e localização desativada têm mensagens/alternativas.
- Altura calculada pela proporção intrínseca completa de cada PNG, com larguras desejadas de 240/320/420 dp limitadas à largura disponível. Removidos altura fixa, preenchimento grande e painel em degradê.
- Uma borda externa roxa discreta (~1 dp), fundo transparente e 1 dp de separação para não pintar sobre a arte. Sem cortes, tintas, máscaras ou efeitos sobre os PNGs. Efeitos solares preliminares deixam de ser aplicados nesta etapa; arquivo-fonte mantido sem uso.
- Previsão dinâmica e local permanecem fora da imagem. Personagens, relógios, textos impressos, cores e molduras originais continuam visíveis.
- Testes adicionados para dimensões das 20 imagens (3 tamanhos, 5 larguras, 4 densidades), variação de proporções, posição antiga/inválida/imprecisa, oscilação, viagem e linha internacional de data.

## Verificado localmente
- 20/20 SHA-256 originais preservados.
- Dimensões dos 20 fixtures de teste conferidas contra o cabeçalho real de cada PNG.
- `git diff --check`: sem erros.
- Testes JVM e compilação desta etapa: aguardando Actions; não confundir com a validação anterior de ce8f9b3.

## Testes ainda necessários no aparelho
- Conceder/negar/revogar permissão, permissão temporária, retorno das configurações e localização desativada.
- Buscar cidades homônimas; testar rede ausente e aparelho sem Geocoder/provedor de rede.
- Trocar de cidade fisicamente e retornar ao launcher; confirmar atualização e consumo de bateria.
- Inspecionar as 20 artes nos três tamanhos, em retrato/paisagem e diferentes densidades/tamanhos de fonte, inclusive relógios/textos/molduras nas bordas.
- Validar que gaveta, atalhos, dock e gestos continuam funcionando. A compilação não substitui esses testes.
