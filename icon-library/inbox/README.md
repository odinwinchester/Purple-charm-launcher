# Área de publicação dos ícones Purple Charm

Esta é uma branch separada da main protegida, destinada exclusivamente à biblioteca remota.

Para adicionar um ícone pelo navegador do GitHub:
1. Entre em `icon-library/inbox/` nesta branch e escolha **Add file → Upload files**.
2. Envie um PNG transparente de até 2 MiB, entre 32 e 2048 pixels por dimensão.
3. Nomeie o PNG com o **packageName Android exato**, por exemplo `com.example.app.png`.
4. Confirme o commit **nesta branch**. O GitHub Actions valida o PNG, registra o pacote, copia a imagem para `icon-library/`, atualiza `catalog.json` e remove o arquivo de entrada.

O upload é intencionalmente manual. Nunca envie credenciais.
Não modifique imagens aprovadas; o script mantém os bytes originais.
Se a validação falhar, o catálogo não será atualizado.

IMPORTANTE: O aplicativo atual ainda consulta `main/icon-library/catalog.json`.
Esta área de publicação NÃO fica visível no aplicativo até o leitor Android
ser configurado para consultar a branch `icon-library-publishing` em uma
atualização única, ou até uma integração alternativa ser implementada.
