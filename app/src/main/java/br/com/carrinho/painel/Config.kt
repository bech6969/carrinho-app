package br.com.carrinho.painel

import com.hoho.android.usbserial.driver.UsbSerialPort

/** Ajustes do carrinho e do inversor WEG CVW300. Edite aqui. */
object Config {
    /** Repositorio do GitHub de onde o app baixa as atualizacoes (Releases). */
    const val GITHUB_REPO = "bech6969/carrinho-app"
    /** Repositorio PRIVADO que recebe os logs e backups (Envio.kt). */
    const val REPO_LOGS = "bech6969/carrinho-logs"
    /** Intervalo do envio automatico enquanto o app esta aberto. */
    const val ENVIO_MIN = 15L

    // ---- Comunicacao (igual aos parametros P0308/P0310/P0311 do inversor) ----
    const val BAUD = 19200
    const val PARIDADE = UsbSerialPort.PARITY_EVEN   // P0311 = 1 (padrao): 8 bits, par, 1 stop. Se P0311 = 0: PARITY_NONE
    const val SLAVE_ID = 1

    // ---- Veiculo ----
    /** true = km/h lido do inversor (P0008, usa P0124 diametro da roda e P0125 reducao). false = rpm */
    const val MOSTRAR_KMH = true
    /** Senha gravada em P0000 antes de alterar parametros (padrao WEG costuma ser 5). */
    const val SENHA_P0000 = 5
    /** Tensao de REPOUSO -> % (48 V chumbo-acido, 24 celulas). Para litio, troque a tabela. */
    val TABELA_BAT = listOf(
        45.4 to 0, 46.0 to 10, 46.6 to 20, 47.2 to 30, 47.8 to 40, 48.4 to 50,
        49.0 to 60, 49.5 to 70, 50.0 to 80, 50.5 to 90, 50.9 to 100
    )
    /** Resistencia interna inicial (ohm); o app ajusta sozinho com o uso (Bateria.kt). */
    const val R_BAT_INICIAL = 0.03
    /** Correcao da velocidade (P0008). Medido no GPS: o inversor mostrava o dobro -> 0,5.
     *  Pode ser recalibrado no app: toque longo no numero da velocidade. */
    const val FATOR_VEL_PADRAO = 0.5
    const val RPM_TROCA_MAX = 50   // so troca F/R abaixo dessa rotacao
    const val FALHAS_P_NEUTRO = 5  // ciclos sem resposta -> neutro
    /** Diagnostico: telemetria a 5 Hz andando (e acelerador a cada ciclo). Gera ~2 MB/hora. */
    const val LOG_RAPIDO = true
    const val CICLO_MS = 100L      // periodo de leitura/escrita (alimenta watchdog P0314)

    // ---- Modos de conducao: velocidade maxima (rpm) gravada em P0134 ----
    // Confira no manual do seu CVW300 se P0134 e a velocidade maxima.
    val NOME_MODO = arrayOf("ECO", "NORMAL", "SPORT")
    val VEL_MAX_MODO = intArrayOf(1500, 2500, 3500)

    // ---- Registradores (endereco = numero do parametro) ----
    const val R_RPM = 2        // P0002
    const val R_CORRENTE = 3   // P0003 (x10)
    const val R_VBAT = 4       // P0004 (x10)
    const val R_KMH = 8        // P0008 velocidade do veiculo (x10)
    const val R_TEMP = 30      // P0030 temperatura MOSFET (confira escala)
    const val R_VELMAX = 134   // P0134
    const val R_ESTADO = 680   // P0680
    const val R_CONTROLE = 682 // P0682

    // ---- Palavra de controle P0682 ----
    const val CMD_FRENTE = 0x0007  // habilita rampa + geral + horario
    const val CMD_RE = 0x0003      // habilita rampa + geral, anti-horario
    const val CMD_NEUTRO = 0x0000
    const val CMD_RESET = 0x0080
    const val CMD_REMOTO = 0x0010  // bit 4: situacao REMOTO (vale com P0220 = 5 ou 6)
}
