# Componentes de terceiros incorporados

## PurpleCircleDrawable.kt
- Projeto de origem: OpenLauncher, https://github.com/OpenLauncherTeam/openlauncher
- Arquivo de origem: app/src/main/java/com/benny/openlauncher/viewutil/CircleDrawable.java
- Licença de origem: Apache License, Version 2.0
- Texto integral da licença: https://www.apache.org/licenses/LICENSE-2.0
- Modificações: reimplementação em Kotlin, remoção da dependência Tool, ajuste do desenho para bounds e finalização da animação.
- Este aviso deve acompanhar qualquer distribuição do componente adaptado. Verificar avisos NOTICE adicionais do projeto de origem antes de distribuir.

Este arquivo não importa o motor completo do OpenLauncher. Não implica compatibilidade ou teste de compilação.

## Previsão meteorológica
- Dados: MET Norway, Norwegian Meteorological Institute, https://www.met.no/
- API Locationforecast: https://api.met.no/weatherapi/locationforecast/2.0/documentation
- Licença dos dados: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- Modificações: seleção horária, tradução das condições, arredondamento e conversão de vento de m/s para km/h.
- Os valores impressos nos PNGs originais são ilustrativos; a previsão consultada é apresentada separadamente.
