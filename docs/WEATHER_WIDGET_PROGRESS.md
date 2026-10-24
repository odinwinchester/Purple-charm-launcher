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
