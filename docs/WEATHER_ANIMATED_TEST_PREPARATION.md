# Purple Charm — fundos meteorológicos animados (preparação do APK de teste)

## Base protegida
- Branch de trabalho: `codex/weather-animated-backgrounds-test`
- Base: `46b1922c18594c2c3e23fd9bedd381bfc141a7e9`
- Não alterar `main`, dock, gaveta, gestos ou os 26 PNGs meteorológicos originais.

## Escopo
- Toque simples no widget abre `ExpandedWeatherActivity`.
- Página expandida com fundo animado independente da arte original do widget.
- Violet Glass padrão; Crystal Clear opcional.
- Seleção de fundo segundo condição real, dia/noite e variações disponíveis.
- Sem simular dados meteorológicos nem afirmar alertas inexistentes.
- Voltar retorna à Home; toque longo continua para mover/redimensionar.

## Situação dos recursos
Os 26 PNGs existentes em `drawable-nodpi` são artes de widget, **não** 26 fundos animados verticais. Quatro protótipos de movimento foram aprovados na conversa, mas não foram incorporados a esta branch. Não marcar os 26 fundos como produzidos sem inventário e inspeção dos arquivos finais.

## Critérios de aceite antes do APK
1. Gerar/recuperar 26 fundos verticais distintos e verificar formatos, dimensões, duração e quantidade de quadros.
2. Preservar elementos estáticos e animar apenas fenômenos adequados; gelo não ondula como água líquida.
3. Implementar carregamento de um fundo por vez e pausar animação ao sair da Activity.
4. Confirmar modo Violet Glass e opção Crystal Clear sem sacrificar legibilidade.
5. Testes automatizados, compilação Android e auditoria de hashes dos PNGs originais.
6. Produzir APK **de teste** via Actions, verificar pacote, versão e assinatura; sem release ou merge.
7. Testar no POCO antes de declarar concluído.

## Estado
Somente preparação. Nenhum APK gerado ou integração de WebP concluída neste documento.
