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

    val conectado get() = mb != null
    val parado get() = rpm == 0 && !girando
}
