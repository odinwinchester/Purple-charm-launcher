# Coleção visual de 26 cenários — contrato de produção

**Estado:** direção artística e mapeamento definidos; animações finais ainda não produzidas nem validadas.

## Regra principal
A arte do widget exibido na Home determina o cenário da página expandida no momento do toque. O identificador `art_slug` é a chave de ligação, e **não** a condição meteorológica isoladamente. Isso preserva seleção manual, fases lunares e as artes especiais.

## Cenários (um para cada slug do WeatherArtworkCatalog)
| slug | cenário | movimento |
|---|---|---|
| dia_quente | Sol cristalino quente | cintilação térmica, reflexos |
| dia_limpo | Sol cristalino, lago | cascata e água corrente |
| dia_nublado | Nuvens solares | nuvens suaves |
| nascer_do_sol | Aurora violeta | luz crescente |
| lua_01_lua_nova_noite_limpa | Lua nova, céu estrelado | estrelas e cascata |
| lua_02_lua_crescente_noite_limpa | Lua crescente, céu estrelado | estrelas e cascata |
| lua_03_lua_cheia_noite_limpa | Lua cheia, céu estrelado | estrelas e cascata |
| lua_04_lua_minguante_noite_limpa | Lua minguante, céu estrelado | estrelas e cascata |
| lua_05_lua_nova_noite_nublada | Lua nova, nuvens | nuvens |
| lua_06_lua_crescente_noite_nublada | Lua crescente, nuvens | nuvens |
| lua_07_lua_cheia_noite_nublada | Lua cheia, nuvens | nuvens |
| lua_08_lua_minguante_noite_nublada | Lua minguante, nuvens | nuvens |
| lua_09_lua_nova_noite_chuvosa | Lua nova, chuva | chuva e água |
| lua_10_lua_crescente_noite_chuvosa | Lua crescente, chuva | chuva e água |
| lua_11_lua_cheia_noite_chuvosa | Lua cheia, chuva | chuva e água |
| lua_12_lua_minguante_noite_chuvosa | Lua minguante, chuva | chuva e água |
| lua_13_lua_nova_noite_fria_neve | Lua nova, neve | neve e névoa |
| lua_14_lua_crescente_noite_fria_neve | Lua crescente, neve | neve e névoa |
| lua_15_lua_cheia_noite_fria_neve | Lua cheia, neve | neve e névoa |
| lua_16_lua_minguante_noite_fria_neve | Lua minguante, neve | neve e névoa |
| sol_dia_chuvoso | Sol entre chuva | chuva e cascata |
| sol_dia_frio | Sol invernal | neve e gelo |
| lua_noite_chuvosa | Lua alternativa, chuva | chuva e lago |
| lua_noite_fria | Lua alternativa, gelo | névoa, brilho de gelo |
| eclipse_solar_frio | Eclipse solar artístico | coroa de luz e neve |
| eclipse_lunar_limpo | Eclipse lunar artístico | estrelas e halo |

## Regras de qualidade
- Cenários verticais originais, **não** os PNGs horizontais dos widgets ampliados.
- Paisagens góticas cristalinas violetas; castelos opcionais, não obrigatórios.
- Arquitetura permanece estática. Água líquida se move; lagos congelados não têm ondulações.
- Não afirmar eclipse astronômico real; as artes especiais só aparecem se selecionadas.
- O catálogo precisa conter exatamente 26 identificadores únicos e correspondência 1:1.
- O poster de referência é um *moodboard*; não é o pacote de 26 fundos nem prova de animação.
- Verificar quadro a quadro, duração, loops, resolução e tamanho de cada arquivo antes de integrar.
- APK somente após build, testes e validação de assinatura, sem merge ou release.
