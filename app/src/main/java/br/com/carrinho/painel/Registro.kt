package br.com.carrinho.painel

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Log do carrinho, um arquivo CSV por dia em files/logs/:
 *  - EVENTO: conexao, marchas, modos, falhas, alarmes, configuracoes, backups
 *  - DADOS : telemetria (1 s andando, 10 s parado)
 * Mantem os ultimos [MAX_DIAS] arquivos.
 */
object Registro {
    private const val MAX_DIAS = 60
    private const val CABECALHO =
        "data_hora;tipo;marcha;modo;kmh;rpm;corrente_A;bateria_V;temp_mosfet_C;torque_pct;acelerador_pct;estado_P0006;P0680_hex;alarme;falha;descricao"

    private val exec = Executors.newSingleThreadExecutor()
    private val fmtData = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fmtHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private lateinit var pasta: File

    /** Ultimos eventos para exibir na tela. */
    val recentes = ArrayDeque<String>()

    /** Ultima telemetria conhecida (preenche as colunas dos eventos). */
    @Volatile var ultimaLinhaDados: String = ";;;;;;;;;;;;"

    fun iniciar(ctx: Context) {
        if (::pasta.isInitialized) return
        pasta = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs").apply { mkdirs() }
        exec.execute { limparAntigos() }
    }

    fun pastaLogs(): File = pasta
    fun arquivoHoje(): File = File(pasta, "log_${fmtData.format(Date())}.csv")

    fun evento(tipo: String, descricao: String) {
        val agora = Date()
        val txt = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(agora)}  $tipo  $descricao"
        synchronized(recentes) {
            recentes.addFirst(txt)
            while (recentes.size > 300) recentes.removeLast()
        }
        escreve(agora, "EVENTO:$tipo", ultimaLinhaDados, descricao)
    }

    fun dados(marcha: String, modo: String, kmh: Double, rpm: Int, corrente: Double, vbat: Double,
              temp: Double, torque: Double, acelerador: Double, estado: Int, logico: Int,
              alarme: Int, falha: Int) {
        val linha = String.format(Locale.US,
            "%s;%s;%.1f;%d;%.1f;%.1f;%.1f;%.1f;%.2f;%d;%04X;%d;%d",
            marcha, modo, kmh, rpm, corrente, vbat, temp, torque, acelerador, estado, logico, alarme, falha)
        ultimaLinhaDados = linha
        escreve(Date(), "DADOS", linha, "")
    }

    private fun escreve(quando: Date, tipo: String, dados: String, descricao: String) {
        if (!::pasta.isInitialized) return
        val linha = "${fmtHora.format(quando)};$tipo;$dados;${descricao.replace(';', ',').replace('\n', ' ')}"
        exec.execute {
            try {
                val f = File(pasta, "log_${fmtData.format(quando)}.csv")
                val novo = !f.exists()
                FileWriter(f, true).use { w ->
                    if (novo) w.write(CABECALHO + "\n")
                    w.write(linha + "\n")
                }
            } catch (_: Exception) { }
        }
    }

    private fun limparAntigos() {
        val logs = pasta.listFiles { f -> f.name.startsWith("log_") }?.sortedByDescending { it.name } ?: return
        logs.drop(MAX_DIAS).forEach { it.delete() }
    }
}
