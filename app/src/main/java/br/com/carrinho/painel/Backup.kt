package br.com.carrinho.painel

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup de TODOS os parametros do inversor em CSV (files/backups/).
 * Formato: parametro;nome;tipo;bruto;valor;unidade;opcao
 *   tipo = rw (configuravel) | ro (somente leitura, so registro)
 *   bruto = valor no registrador (usado na restauracao)
 */
object Backup {

    fun pasta(ctx: Context) =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups").apply { mkdirs() }

    fun lista(ctx: Context): List<File> =
        pasta(ctx).listFiles { f -> f.name.endsWith(".csv") }?.sortedByDescending { it.name } ?: emptyList()

    fun existe(ctx: Context) = lista(ctx).isNotEmpty()

    private fun fator(dec: Int): Double { var f = 1.0; repeat(dec) { f *= 10 }; return f }

    fun formata(p: Param, bruto: Int): String =
        if (p.dec == 0) bruto.toString() else String.format(Locale.US, "%.${p.dec}f", bruto / fator(p.dec))

    /** Le tudo do inversor e grava o arquivo. Retorna o arquivo e quantos falharam. */
    fun fazer(ctx: Context, mb: ModbusRtu, motivo: String, progresso: (Int, Int) -> Unit): Pair<File, Int> {
        val todos = (Parametros.todos.map { it to "rw" } + Parametros.somenteLeitura.map { it to "ro" })
            .sortedBy { it.first.num }
        val linhas = StringBuilder()
        var falhas = 0
        var versao = "?"; var modelo = "?"

        todos.forEachIndexed { i, (p, tipo) ->
            progresso(i + 1, todos.size)
            val r = mb.readHolding(p.num, 1)?.get(0)
            if (r == null) {
                falhas++
                linhas.append("${p.codigo};${p.nome};$tipo;;ERRO_LEITURA;${p.unidade};\n")
            } else {
                val v = if (p.assinado) r.toShort().toInt() else r
                val op = p.opcoes?.firstOrNull { it.first == v }?.second ?: ""
                linhas.append("${p.codigo};${p.nome};$tipo;$v;${formata(p, v)};${p.unidade};$op\n")
                if (p.num == 23) versao = formata(p, v)
                if (p.num == 295) modelo = when (v) { 0 -> "50 A / 100 A"; 1 -> "100 A / 200 A"; 2 -> "200 A / 400 A"; else -> "$v" }
            }
        }

        val agora = Date()
        val nome = "backup_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(agora)}.csv"
        val f = File(pasta(ctx), nome)
        f.writeText(
            "# Backup CVW300 - ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(agora)}\n" +
            "# Motivo: $motivo\n" +
            "# Versao de software (P0023): $versao | Modelo (P0295): $modelo\n" +
            "# Parametros lidos: ${todos.size - falhas} de ${todos.size}\n" +
            "parametro;nome;tipo;bruto;valor;unidade;opcao\n" + linhas
        )
        Registro.evento("BACKUP", "$nome ($motivo) - ${todos.size - falhas}/${todos.size} lidos")
        return f to falhas
    }

    /** Valores brutos dos parametros configuraveis (rw) de um backup. */
    fun lerValores(f: File): Map<Int, Int> {
        val mapa = mutableMapOf<Int, Int>()
        f.readLines().forEach { l ->
            if (l.startsWith("#") || l.startsWith("parametro")) return@forEach
            val c = l.split(";")
            if (c.size >= 4 && c[2] == "rw") {
                val num = c[0].removePrefix("P").toIntOrNull()
                val v = c[3].toIntOrNull()
                if (num != null && v != null) mapa[num] = v
            }
        }
        return mapa
    }
}
