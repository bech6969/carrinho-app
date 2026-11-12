# App Carrinho — painel Android para inversor WEG CVW300

Tablet Android → USB OTG → adaptador USB-RS485 → CVW300 (XC9-05 = A, XC9-06 = B, + GND)

## Build e atualização
Cada push na `main` roda o GitHub Actions, que compila e publica uma Release (`Carrinho.apk` + `versao.json`).
O app instalado verifica a Release ao abrir e oferece atualizar sozinho.
Requer os secrets `KEYSTORE_B64` e `KEYSTORE_PASS` (assinatura fixa) e o repositório público.

## Ajustes
Tudo em `app/src/main/java/br/com/carrinho/painel/Config.kt`:
- `PARIDADE` conforme P0311 do inversor (padrão 1 = par)
- `SENHA_P0000` senha de acesso para alterar parâmetros
- `V_BAT_VAZIA` / `V_BAT_CHEIA` da sua bateria
- `MOSTRAR_KMH` (km/h vem do P0008, calculado pelo inversor com P0124/P0125)
- `VEL_MAX_MODO` (rpm dos modos ECO/NORMAL/SPORT, gravado em P0134 — confira no manual)

## No inversor
P0223 = 5 · P0224 = 2 · P0313 = desabilita geral · P0314 = 0,5 s · pedal no analógico.

## Segurança embutida
- F↔R só com o carrinho parado
- App fora da tela, adaptador removido ou 5 falhas seguidas → neutro
- Comando enviado a cada 100 ms (watchdog do inversor para o carrinho se o app travar)
- Primeiro teste com as rodas suspensas

## Tela de Configurações (⚙ no painel)
- Todos os parâmetros configuráveis do manual do CVW300 G2, por grupo, com busca
- "Ler do inversor" → edite → "Salvar": grava só o que mudou, confere lendo de volta
- Bloqueia gravação com o carrinho em movimento; avisos ⚠ nos parâmetros críticos
- "Valores padrão" preenche com o padrão de fábrica (só grava ao tocar em Salvar)

## Backup e Log (📋 no painel)
- **Backup inicial automático** na primeira conexão: lê TODOS os parâmetros (configuráveis + leitura) antes de qualquer alteração
- "Fazer backup agora" a qualquer momento; "Restaurar" volta o inversor para um backup (faz backup de segurança antes, grava P0303)
- **Log diário** (CSV, um arquivo por dia, 60 dias): eventos (marchas, modos, falhas/alarmes com descrição, comunicação, configurações alteradas, backups) + telemetria (1 s andando, 10 s parado)
- Botão "Enviar" compartilha qualquer arquivo (WhatsApp, Drive, e-mail) — mande o CSV para o Claude analisar
- Arquivos também ficam em Android/data/br.com.carrinho.painel/files/ (acessíveis pelo cabo USB no PC)
