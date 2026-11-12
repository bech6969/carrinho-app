package br.com.carrinho.painel

/** Conexao compartilhada entre o painel e a tela de configuracoes. */
object Conexao {
    @Volatile var mb: ModbusRtu? = null
    @Volatile var rpm: Int = 0
    @Volatile var girando: Boolean = false

    // ---- Diagnostico (tela DiagnosticoActivity) ----
    @Volatile var diagAtivo = false
    @Volatile var freqHz = 0.0
    @Volatile var rpmEncoder = -1
    @Volatile var refRpm = 0
    @Volatile var acelerador = 0.0
    @Volatile var torque = 0.0
    @Volatile var corrente = 0.0
    @Volatile var atualizado = 0L
    /** P0680 (estado logico): bit 8 girando, bit 10 horario. */
    @Volatile var logico = 0

    val conectado get() = mb != null
    val parado get() = rpm == 0 && !girando
}
