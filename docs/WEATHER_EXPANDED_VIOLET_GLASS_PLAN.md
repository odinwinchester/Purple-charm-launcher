# Purple Charm — tela meteorológica expandida (escopo aprovado)

## Objetivo
Criar uma tela Android compartilhada para as 26 artes meteorológicas existentes, sem substituir ou modificar os PNGs originais. A seleção de arte deve usar a condição meteorológica, a temperatura e o período solar local. O toque no widget abre a tela; voltar retorna à Home; toque longo continua reservado a mover/redimensionar.

## Aparência aprovada
- Fundo: arte ORIGINAL selecionada do repositório, sem redesenho de personagens, coroas, raios, luas ou cristais.
- Sobreposição: vidro fumê VIOLETA com **95% de transparência (5% de opacidade de preenchimento)**; preservar legibilidade de texto/ícones e molduras discretas.
- Uma estrutura reutilizável para todas as artes, evitando 26 Activities e 26 layouts duplicados.
- Dados reais renderizados em componentes Android, jamais texto gravado na imagem: temperatura, cidade, condição, sensação térmica (calculada/rotulada), umidade, vento, pressão, UV se disponível, previsão horária, previsão de cinco dias, nascer/pôr do sol e fase lunar quando integrados.
- MET Norway continua como fonte meteorológica. INMET é fonte complementar proposta para avisos brasileiros, **dependente de validação de endpoint e área de cobertura**. Se indisponível, mostrar “Alertas indisponíveis”, nunca “Sem alertas”.

## Critérios antes de gerar APK de teste
1. Inventariar as 26 artes reais, registrar caminhos, dimensões e hashes; verificar a existência de cada recurso e seu mapeamento.
2. Verificar a arquitetura e implementar a Activity/Composable reutilizável sem afetar dock, gaveta, gestos e wallpaper.
3. Não confundir números impressos nos PNGs com dados ao vivo: se necessário, usar composição derivada e aprovada, mantendo arquivos originais intactos.
4. Corrigir/validar temperatura, localização, fusos horários, dados ausentes e offline; manter as regras de cache/limites do MET Norway.
5. Compilar e rodar testes no GitHub Actions; conferir hashes das artes, applicationId, versionCode e assinatura do APK.
6. Publicar **somente APK de teste** como artefato do Actions; não publicar release, não alterar main, não fazer merge.
7. Teste final no aparelho POCO pelo proprietário antes de declarar etapa concluída.

## Estado
Documento de implementação inicial. Nenhuma tela expandida implementada nem APK gerado nesta etapa. Mockups anteriores são ilustrações e não substituem artes originais.
