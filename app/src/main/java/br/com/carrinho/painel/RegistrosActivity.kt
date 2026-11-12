package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Log de eventos ao vivo, arquivos de log, backup e restauracao de parametros. */
class RegistrosActivity : Activity() {

    private lateinit var eventos: TextView
    private lateinit var info: TextView
    private lateinit var arquivos: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ocupado = false

    private val atualizador = object : Runnable {
        override fun run() {
            eventos.text = synchronized(Registro.recentes) { Registro.recentes.take(150).joinToString("\n") }
                .ifEmpty { "Nenhum evento ainda." }
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Registro.iniciar(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0F12"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val barra = LinearLayout(this)
        listOf(
            botao("← Painel", "#2A2F36") { finish() },
            botao("Fazer backup agora", "#1A73E8") { fazerBackup() },
            botao("Compartilhar log de hoje", "#1E8E3E") { compartilhar(Registro.arquivoHoje()) },
            botao("Verificar atualização", "#8430CE") {
                info.text = "Verificando atualização…"
                Atualizador.verificar(this, silencioso = false) { info.text = it }
            }
        ).forEachIndexed { i, b ->
            barra.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) marginStart = dp(10) })
        }
        raiz.addView(barra)

        info = TextView(this).apply {
            setTextColor(Color.parseColor("#9AA0A6")); textSize = 15f
            text = "Versão ${Atualizador.nomeInstalado(this@RegistrosActivity)} · Arquivos em: Android/data/$packageName/files/ (logs e backups)"
        }
        raiz.addView(info, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val colunas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // Eventos ao vivo
        val colEventos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colEventos.addView(titulo("EVENTOS (ao vivo)"))
        eventos = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f
            setTextColor(Color.parseColor("#E8EAED"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cantos("#1B1F24")
        }
        colEventos.addView(ScrollView(this).apply { addView(eventos) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        colunas.addView(colEventos, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.3f))

        // Arquivos
        val colArq = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colArq.addView(titulo("ARQUIVOS"))
        arquivos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colArq.addView(ScrollView(this).apply { addView(arquivos) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        colunas.addView(colArq, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(12) })

        raiz.addView(colunas, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        setContentView(raiz)
        listarArquivos()
    }

    override fun onResume() { super.onResume(); handler.post(atualizador) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(atualizador) }

    // ---------------- Arquivos ----------------

    private fun listarArquivos() {
        arquivos.removeAllViews()
        val backups = Backup.lista(this)
        val logs = Registro.pastaLogs().listFiles { f -> f.name.endsWith(".csv") }
            ?.sortedByDescending { it.name } ?: emptyList()

        arquivos.addView(subtitulo("Backups de parâmetros (${backups.size})"))
        if (backups.isEmpty()) arquivos.addView(subtitulo("Nenhum backup ainda."))
        backups.forEach { f -> arquivos.addView(linhaArquivo(f, restaurar = true)) }

        arquivos.addView(subtitulo("Logs diários (${logs.size})"))
        logs.forEach { f -> arquivos.addView(linhaArquivo(f, restaurar = false)) }
    }

    private fun linhaArquivo(f: File, restaurar: Boolean): LinearLayout {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cantos("#1B1F24")
            setPadding(dp(12), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        linha.addView(TextView(this).apply {
            text = "${f.name}\n${String.format(Locale.US, "%.1f KB", f.length() / 1024.0)}"
            setTextColor(Color.WHITE); textSize = 14f
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        linha.addView(botao("Enviar", "#2A2F36") { compartilhar(f) },
            LinearLayout.LayoutParams(dp(100), dp(44)).apply { marginStart = dp(6) })
        if (restaurar) linha.addView(botao("Restaurar", "#8430CE") { confirmarRestaurar(f) },
            LinearLayout.LayoutParams(dp(110), dp(44)).apply { marginStart = dp(6) })
        return linha
    }

    private fun compartilhar(f: File) {
        if (!f.exists()) { info.text = "Arquivo ainda não existe (nenhum registro hoje)."; return }
        val uri = FileProvider.getUriForFile(this, "$packageName.arquivos", f)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(envio, "Enviar ${f.name}"))
    }

    // ---------------- Backup ----------------

    private fun fazerBackup() {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão com o inversor."; return }
        if (ocupado) return
        ocupado = true
        Thread {
            try {
                val (f, falhas) = Backup.fazer(this, mb, "Backup manual") { i, n ->
                    runOnUiThread { info.text = "Lendo parâmetros… $i de $n" }
                }
                runOnUiThread {
                    info.text = "✔ Backup salvo: ${f.name}" + if (falhas > 0) " — $falhas parâmetro(s) sem resposta" else ""
                    listarArquivos()
                }
            } catch (e: Exception) {
                runOnUiThread { info.text = "Erro no backup: ${e.message}" }
            } finally { ocupado = false }
        }.start()
    }

    // ---------------- Restauracao ----------------

    private fun confirmarRestaurar(f: File) {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão com o inversor."; return }
        if (ocupado) return
        if (!Conexao.parado) { info.text = "Pare o carrinho para restaurar."; return }
        ocupado = true
        info.text = "Comparando backup com o inversor…"
        Thread {
            val alvo = Backup.lerValores(f).filterKeys { it !in Parametros.naoRestaurar }
            val porNum = Parametros.todos.associateBy { it.num }
            val diferencas = mutableListOf<Triple<Param, Int?, Int>>()
            for ((num, v) in alvo.toSortedMap()) {
                val p = porNum[num] ?: continue
                val atual = mb.readHolding(num, 1)?.get(0)?.let { if (p.assinado) it.toShort().toInt() else it }
                if (atual != v) diferencas += Triple(p, atual, v)
            }
            ocupado = false
            runOnUiThread {
                if (diferencas.isEmpty()) { info.text = "O inversor já está igual a ${f.name}."; return@runOnUiThread }
                val resumo = diferencas.joinToString("\n") { (p, a, v) ->
                    "${p.codigo} ${p.nome}: ${a?.let { Backup.formata(p, it) } ?: "?"} → ${Backup.formata(p, v)}"
                }
                AlertDialog.Builder(this)
                    .setTitle("Restaurar ${diferencas.size} parâmetro(s) de ${f.name}?")
                    .setMessage(resumo + "\n\nAntes de restaurar, o app faz um backup do estado atual.")
                    .setPositiveButton("Restaurar") { _, _ -> restaurar(f, diferencas) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }.start()
    }

    private fun restaurar(origem: File, dif: List<Triple<Param, Int?, Int>>) {
        val mb = Conexao.mb ?: return
        ocupado = true
        Thread {
            try {
                Backup.fazer(this, mb, "Antes de restaurar ${origem.name}") { i, n ->
                    runOnUiThread { info.text = "Backup de segurança… $i de $n" }
                }
                Registro.evento("RESTAURAR", "Início: ${origem.name} (${dif.size} parâmetros)")
                mb.writeSingle(0, Config.SENHA_P0000)
                val erros = mutableListOf<String>()
                dif.forEachIndexed { i, (p, _, v) ->
                    runOnUiThread { info.text = "Restaurando ${i + 1} de ${dif.size}: ${p.codigo}" }
                    if (!Conexao.parado) { erros += "${p.codigo} (carrinho em movimento)"; return@forEachIndexed }
                    val ok = mb.writeSingle(p.num, v) &&
                        mb.readHolding(p.num, 1)?.get(0)?.let { (if (p.assinado) it.toShort().toInt() else it) == v } == true
                    if (!ok) erros += "${p.codigo} ${p.nome}"
                }
                val memoria = erros.size < dif.size && mb.writeSingle(303, 1)
                Registro.evento("RESTAURAR", "Fim: ${dif.size - erros.size} ok, ${erros.size} falha(s)" +
                    (if (memoria) ", gravado na memória" else ", NÃO gravou na memória") +
                    (if (erros.isNotEmpty()) " - " + erros.joinToString(", ") else ""))
                runOnUiThread {
                    listarArquivos()
                    info.text = if (erros.isEmpty()) "✔ Restauração concluída e salva na memória."
                                else "Restauração com ${erros.size} falha(s)."
                    if (erros.isNotEmpty()) AlertDialog.Builder(this).setTitle("Não restaurados")
                        .setMessage(erros.joinToString("\n") + "\n\nAlguns parâmetros só aceitam alteração com o inversor desabilitado ou dependem de outros (ex.: P0400 ≤ P0296). Tente restaurar de novo.")
                        .setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread { info.text = "Erro na restauração: ${e.message}" }
            } finally { ocupado = false }
        }.start()
    }

    // ---------------- Utilitarios ----------------

    private fun titulo(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#8AB4F8")); textSize = 15f
        setPadding(dp(4), dp(6), 0, dp(6))
    }

    private fun subtitulo(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#9AA0A6")); textSize = 14f
        setPadding(dp(4), dp(12), 0, dp(2))
    }

    private fun cantos(cor: String) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(Color.parseColor(cor))
    }

    private fun botao(txt: String, cor: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 15f
        setTextColor(Color.WHITE)
        background = cantos(cor)
        setOnClickListener { acao() }
    }
}
