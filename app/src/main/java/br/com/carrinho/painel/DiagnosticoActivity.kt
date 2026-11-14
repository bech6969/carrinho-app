package br.com.carrinho.painel

import android.app.Activity
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
import java.util.Locale

/**
 * Teste do encoder: compara a rotacao medida pelo encoder (P0038) com a esperada pela
 * frequencia que o inversor manda ao motor (P0005 x 120 / polos). Se der ~2x, o numero de
 * pulsos do encoder (P0405) esta errado.
 */
class DiagnosticoActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var linhas: TextView
    private lateinit var veredito: TextView
    private val razoes = ArrayDeque<Double>()
    private var p405: Int? = null
    private var polos = 4

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val tick = object : Runnable {
        override fun run() { atualizar(); handler.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0F12"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        raiz.addView(Button(this).apply {
            text = "← Painel"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#2A2F36")) }
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(140), dp(48)))

        raiz.addView(TextView(this).apply {
            text = "TESTE DO ENCODER — rodas suspensas, chave em F, pedal parado num ponto (acima de 10 Hz)"
            setTextColor(Color.parseColor("#8AB4F8")); textSize = 15f; setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
        })
        veredito = TextView(this).apply {
            textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(dp(12), dp(14), dp(12), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B1F24")) }
        }
        raiz.addView(veredito, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        raiz.addView(Button(this).apply {
            text = "Ajuste do regulador (P0162) →"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#8430CE")) }
            setOnClickListener { startActivity(android.content.Intent(this@DiagnosticoActivity, ReguladorActivity::class.java)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
        raiz.addView(Button(this).apply {
            text = "Curva do pedal (SoftPLC) →"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1A73E8")) }
            setOnClickListener { startActivity(android.content.Intent(this@DiagnosticoActivity, SoftplcActivity::class.java)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
        linhas = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 20f; setTextColor(Color.parseColor("#E8EAED"))
            setPadding(dp(4), dp(12), dp(4), dp(4))
        }
        raiz.addView(ScrollView(this).apply { addView(linhas) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)

        // le P0405 (pulsos do encoder) e P0402/P0403 (para calcular os polos) uma vez
        Conexao.mb?.let { mb ->
            Thread {
                val ppr = mb.readHolding(405, 1)?.get(0)
                val rpmNom = mb.readHolding(402, 1)?.get(0)
                val hzNom = mb.readHolding(403, 1)?.get(0)
                runOnUiThread {
                    p405 = ppr
                    if (rpmNom != null && hzNom != null && rpmNom > 0)
                        polos = (Math.round(120.0 * hzNom / rpmNom / 2) * 2).toInt().coerceIn(2, 12)
                }
            }.start()
        }
    }

    override fun onResume() { super.onResume(); Conexao.diagAtivo = true; Conexao.rpmEncoder = -1; handler.post(tick) }
    override fun onPause() { super.onPause(); Conexao.diagAtivo = false; handler.removeCallbacks(tick) }

    private fun atualizar() {
        if (!Conexao.conectado) {
            veredito.text = "Sem conexão com o inversor"; linhas.text = ""; return
        }
        val hz = Conexao.freqHz
        val enc = Conexao.rpmEncoder
        val esperado = hz * 120.0 / polos
        if (hz > 10 && enc > 0) {
            razoes.addLast(enc / esperado)
            while (razoes.size > 12) razoes.removeFirst()
        } else razoes.clear()
        val r = if (razoes.size >= 6) razoes.average() else null

        veredito.text = when {
            enc < 0 -> "Lendo o encoder…"
            r == null -> "Acelere até mais de 10 Hz e segure o pedal parado"
            r in 1.8..2.2 -> "≈ ×2  →  P0405 ERRADO\nmude P0405 de ${p405 ?: "?"} para ${(p405 ?: 256) * 2}"
            r in 0.4..0.6 -> "≈ ×0,5  →  P0405 ERRADO\nmude P0405 de ${p405 ?: "?"} para ${(p405 ?: 256) / 2}"
            r in 0.9..1.12 -> "≈ ×1  →  encoder OK\na velocidade se corrige no P0124/P0125"
            else -> "Razão %.2f — fora do esperado. Mande print.".format(Locale.US, r)
        }
        (veredito.background as? GradientDrawable)?.setColor(Color.parseColor(when {
            r == null -> "#1B1F24"; r in 0.9..1.12 -> "#1E5E2E"; else -> "#7A4A00"
        }))
        linhas.text = String.format(Locale.US,
            "Frequência (P0005)     %7.1f Hz\n" +
            "Rotação esperada       %7.0f rpm   (Hz × 120 / %d polos)\n" +
            "Rotação encoder (P0038)%7s rpm\n" +
            "Razão encoder/esperada %7s\n" +
            "Pulsos encoder (P0405) %7s ppr\n\n" +
            "Referência (P0001)     %7d rpm\n" +
            "Acelerador (P0018)     %7.1f %%\n" +
            "Torque (P0009)         %7.1f %%\n" +
            "Corrente (P0003)       %7.1f A",
            hz, esperado, polos,
            if (enc < 0) "--" else enc.toString(),
            r?.let { "%.2f".format(Locale.US, it) } ?: "--",
            p405?.toString() ?: "--",
            Conexao.refRpm, Conexao.acelerador, Conexao.torque, Conexao.corrente)
    }
}
