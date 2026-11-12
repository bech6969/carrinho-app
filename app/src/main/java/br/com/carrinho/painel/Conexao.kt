package br.com.carrinho.painel

/** Conexao compartilhada entre o painel e a tela de configuracoes. */
object Conexao {
    @Volatile var mb: ModbusRtu? = null
    @Volatile var rpm: Int = 0
    @Volatile var girando: Boolean = false

    val conectado get() = mb != null
    val parado get() = rpm == 0 && !girando
}
