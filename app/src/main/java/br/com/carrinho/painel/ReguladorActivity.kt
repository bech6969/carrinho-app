package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * Ajuste do ganho integral do regulador de velocidade (P0162) em degraus, de 0 ate o de fabrica.
 * Com P0162 = 0 a parte integral fica "congelada" depois que o regulador satura: o motor fica com
 * torque preso ao soltar o pedal (re ou andar sozinho). Cada degrau e gravado so na RAM; quando um
 * valor ficar bom, "Salvar na memoria" grava P0303.
 *
 * A tela mede sozinha cada soltada do pedal: tempo para parar, se deu re e o torque com pedal solto.
 */
class ReguladorActivity : Activity() {

    private val degraus = intArrayOf(0, 5, 10, 15, 23)     // P0162 bruto (x1000): 0,000 .. 0,023 (fabrica)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var info: TextView
    private lateinit var atualTxt: TextView
    private lateinit var aoVivo: TextView
    private lateinit var resultados: TextView
    private lateinit var botoes: LinearLayout
    private var original: Int? = null
    private var p162: Int? = null
    private var p161: Int? = null
    @Volatile private var ocupado = false
    private val historico = mutableListOf<String>()

    // ---- medicao da soltada do pedal ----
    private var armado = false
    private var emTeste = false
    private var t0 = 0L
    private var rpmSolta = 0
    private var horarioSolta = true
    private var deuRe = false
    private var torqueMax = 0.0     // torque "motor" (mesmo sentido do giro) com pedal solto, apos 1 s

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun f3(v: Int?) = v?.let { String.format(Locale.US, "%.3f", it / 1000.0).replace('.', ',') } ?: "?"
    private fun f1(v: Int?) = v?.let { String.format(Locale.US, "%.1f", it / 10.0).replace('.', ',') } ?: "?"

    private val tick = object : Runnable {
        override fun run() { medir(); handler.postDelayed(this, 100) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0F12"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(botao("← Voltar", "#2A2F36") { finish() }, LinearLayout.LayoutParams(dp(130), dp(46)))

        lista.addView(texto("AJUSTE DO REGULADOR DE VELOCIDADE — integral (P0162)", 15f, "#8AB4F8", true))
        lista.addView(texto(
            "Rodas suspensas, \"Comando: CHAVE\" no painel e chave em F. Para cada degrau: acelere forte (acima de 50%) " +
            "e solte o pedal de uma vez, 3 vezes. A tela mede sozinha. Os valores vão só para a RAM: se desligar " +
            "o inversor, volta o que estava salvo.", 14f, "#BDC1C6"))
        atualTxt = texto("Lendo P0161/P0162…", 18f, "#FFFFFF", true)
        lista.addView(atualTxt, lp(8))

        botoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lista.addView(botoes, lp(8))
        val extra = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        extra.addView(botao("Voltar ao original", "#5C1A1A") { original?.let { gravar(it, "original") } }, LinearLayout.LayoutParams(0, dp(50), 1f))
        extra.addView(botao("Salvar na memória (P0303)", "#1E8E3E") { salvarMemoria() },
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(8) })
        lista.addView(extra, lp(8))

        info = texto("", 15f, "#F9AB00")
        lista.addView(info, lp(6))
        aoVivo = texto("", 17f, "#E8EAED").apply { typeface = Typeface.MONOSPACE }
        lista.addView(card(aoVivo), lp(8))
        lista.addView(texto("RESULTADOS (cada soltada do pedal)", 15f, "#8AB4F8", true), lp(12))
        resultados = texto("Nenhum teste ainda.", 15f, "#E8EAED").apply { typeface = Typeface.MONOSPACE }
        lista.addView(card(resultados), lp(6))

        raiz.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        lerAtual()
    }

    override fun onResume() { super.onResume(); Conexao.diagAtivo = true; handler.post(tick) }
    override fun onPause() { super.onPause(); Conexao.diagAtivo = false; handler.removeCallbacks(tick) }

    // ---------------- parametros ----------------

    private fun lerAtual() {
        val mb = Conexao.mb ?: run { atualTxt.text = "Sem conexão com o inversor."; return }
        Thread {
            val a = mb.readHolding(161, 1)?.get(0)
            val b = mb.readHolding(162, 1)?.get(0)
            runOnUiThread {
                p161 = a; p162 = b
                if (original == null) original = b
                montarBotoes()
            }
        }.start()
    }

    private fun montarBotoes() {
        atualTxt.text = "P0162 integral: agora ${f3(p162)}  (fábrica 0,023, original ${f3(original)})\n" +
            "P0161 proporcional: ${f1(p161)}  (fábrica 7,4 — não mexer nesta etapa)"
        botoes.removeAllViews()
        degraus.forEachIndexed { i, v ->
            val atual = v == p162
            val rot = (if (atual) "● " else "") + f3(v) + if (v == 23) "\nfábrica" else ""
            botoes.addView(botao(rot, if (atual) "#8430CE" else "#2A2F36") { if (!atual) gravar(v, "degrau") },
                LinearLayout.LayoutParams(0, dp(58), 1f).apply { if (i > 0) marginStart = dp(6) })
        }
    }

    private fun gravar(v: Int, motivo: String) {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão."; return }
        if (ocupado) return
        if (!Conexao.parado) { info.text = "Pare o motor (rodas paradas) antes de mudar."; return }
        ocupado = true
        info.text = "Gravando P0162 = ${f3(v)}…"
        Thread {
            mb.writeSingle(0, Config.SENHA_P0000)
            val ok = mb.writeSingle(162, v) && mb.readHolding(162, 1)?.get(0) == v
            Registro.evento("REGULADOR", "P0162 ${f3(p162)} → ${f3(v)} ($motivo, só RAM) " + if (ok) "OK" else "FALHOU")
            runOnUiThread {
                ocupado = false
                if (ok) {
                    historico += "── P0162 = ${f3(v)} ──"
                    p162 = v; info.text = "P0162 = ${f3(v)} (só na RAM). Agora acelere forte e solte de uma vez, 3 vezes."
                } else info.text = "O inversor não aceitou. Confira a senha (P0000) e se o motor está parado."
                montarBotoes(); mostrarResultados()
            }
        }.start()
    }

    private fun salvarMemoria() {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão."; return }
        if (!Conexao.parado) { info.text = "Pare o motor antes."; return }
        AlertDialog.Builder(this)
            .setTitle("Salvar P0162 = ${f3(p162)} na memória?")
            .setMessage("Grava P0303 = 1: o valor passa a valer depois de desligar o inversor.")
            .setPositiveButton("Salvar") { _, _ ->
                Thread {
                    val ok = mb.writeSingle(303, 1)
                    Registro.evento("REGULADOR", "P0303 = 1 com P0162 = ${f3(p162)} " + if (ok) "OK" else "FALHOU")
                    runOnUiThread { info.text = if (ok) "✔ Salvo na memória. P0162 = ${f3(p162)}" else "Falhou gravar P0303." }
                }.start()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ---------------- medicao ----------------

    private fun medir() {
        if (!Conexao.conectado) { aoVivo.text = "Sem conexão com o inversor"; return }
        val ac = Conexao.acelerador; val rpm = abs(Conexao.rpm); val ref = Conexao.refRpm
        val tq = Conexao.torque; val horario = (Conexao.logico and (1 shl 10)) != 0
        aoVivo.text = String.format(Locale.US,
            "Acelerador %5.1f %%   Referência %5d rpm\nRotação    %5d rpm  Torque     %6.1f %%\nSentido    %s   %s",
            ac, ref, rpm, tq, if (horario) "frente (H) " else "ré (AH)    ",
            if (emTeste) "MEDINDO… %.1f s".format(Locale.US, (System.currentTimeMillis() - t0) / 1000.0) else "")

        if (!emTeste) {
            if (ac >= 30) armado = true
            if (armado && ac <= 1.0 && rpm > 100) {         // soltou o pedal com o motor girando
                emTeste = true; armado = false; t0 = System.currentTimeMillis()
                rpmSolta = rpm; horarioSolta = horario; deuRe = false; torqueMax = 0.0
            }
            return
        }
        val dt = (System.currentTimeMillis() - t0) / 1000.0
        if (ac > 5) { finaliza("pisou de novo antes de parar", dt); return }
        if (horario != horarioSolta && rpm > 30) deuRe = true
        // torque no mesmo sentido do giro com pedal solto = motor puxando sozinho
        val tqMotor = if (horario == horarioSolta) (if (horarioSolta) tq else -tq) else 0.0
        if (dt > 1.0 && rpm > 50 && tqMotor > torqueMax) torqueMax = tqMotor
        when {
            rpm < 20 && dt > 0.3 -> finaliza(null, dt)
            dt > 15 -> finaliza("NÃO PAROU em 15 s (andando sozinho)", dt)
        }
    }

    private fun finaliza(obs: String?, dt: Double) {
        emTeste = false
        val veredito = when {
            obs != null -> obs
            deuRe -> "DEU RÉ"
            torqueMax > 5 -> "parou, mas com torque preso"
            else -> "OK"
        }
        val linha = String.format(Locale.US, "P0162=%s  de %4d rpm → parou em %4.1f s  torque solto máx %5.1f%%  %s",
            f3(p162), rpmSolta, dt, torqueMax, veredito)
        historico += linha
        Registro.evento("REGULADOR", linha)
        mostrarResultados()
    }

    private fun mostrarResultados() {
        resultados.text = if (historico.isEmpty()) "Nenhum teste ainda." else historico.takeLast(40).joinToString("\n")
    }

    // ---------------- utilitarios ----------------

    private fun lp(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun texto(t: String, tam: Float, cor: String, negrito: Boolean = false) = TextView(this).apply {
        text = t; textSize = tam; setTextColor(Color.parseColor(cor)); if (negrito) setTypeface(null, Typeface.BOLD)
    }

    private fun card(v: TextView) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B1F24")) }
        setPadding(dp(12), dp(10), dp(12), dp(10)); addView(v)
    }

    private fun botao(txt: String, cor: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor(cor)) }
        setOnClickListener { acao() }
    }
}
