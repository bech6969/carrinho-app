package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Monitor ao vivo de 10 parametros quaisquer, escolhidos pelo usuario (toque no quadro para trocar). */
class MonitorActivity : Activity() {

    private val padrao = intArrayOf(1, 2, 18, 9, 3, 4, 30, 680, 1000, 1002)
    private val prefs by lazy { getSharedPreferences("monitor", MODE_PRIVATE) }
    private val nums = IntArray(10)
    private val valores = arrayOfNulls<Int>(10)
    private val minimos = arrayOfNulls<Int>(10)
    private val maximos = arrayOfNulls<Int>(10)
    private val txtNome = arrayOfNulls<TextView>(10)
    private val txtValor = arrayOfNulls<TextView>(10)
    private val txtMinMax = arrayOfNulls<TextView>(10)
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var lendo = false
    private var leituras = 0
    private var falhas = 0

    private val defs by lazy { (Parametros.somenteLeitura + Parametros.todos).associateBy { it.num } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val tick = object : Runnable {
        override fun run() { ler(); handler.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        for (i in 0 until 10) nums[i] = prefs.getInt("p$i", padrao[i])

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0F12"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topo.addView(botao("← Voltar") { finish() }, LinearLayout.LayoutParams(dp(110), dp(44)))
        topo.addView(TextView(this).apply {
            text = "  MONITOR AO VIVO"; textSize = 16f; setTextColor(Color.parseColor("#8AB4F8")); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topo.addView(botao("Zerar mín/máx") { minimos.fill(null); maximos.fill(null) }, LinearLayout.LayoutParams(dp(150), dp(44)))
        raiz.addView(topo)

        val grade = GridLayout(this).apply { columnCount = 5; rowCount = 2 }
        for (i in 0 until 10) {
            val nome = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#9AA0A6")); maxLines = 2 }
            val valor = TextView(this).apply {
                textSize = 30f; setTextColor(Color.WHITE); setTypeface(Typeface.DEFAULT, Typeface.BOLD); gravity = Gravity.CENTER
            }
            val mm = TextView(this).apply { textSize = 11f; setTextColor(Color.parseColor("#6F757C")); gravity = Gravity.CENTER }
            txtNome[i] = nome; txtValor[i] = valor; txtMinMax[i] = mm
            val quadro = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B1F24")) }
                setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(nome); addView(valor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)); addView(mm)
                setOnClickListener { escolher(i) }
            }
            grade.addView(quadro, GridLayout.LayoutParams(
                GridLayout.spec(i / 5, 1f), GridLayout.spec(i % 5, 1f)).apply {
                width = 0; height = 0; setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        raiz.addView(grade, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#9AA0A6")) }
        raiz.addView(status)
        setContentView(raiz)
        for (i in 0 until 10) mostrar(i)
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun ler() {
        val mb = Conexao.mb ?: run { status.text = "Sem conexão com o inversor"; return }
        if (lendo) return
        lendo = true
        val lista = nums.copyOf()
        Thread {
            val r = lista.map { n -> mb.readHolding(n, 1)?.get(0) }
            runOnUiThread {
                lendo = false
                r.forEachIndexed { i, v ->
                    if (lista[i] != nums[i]) return@forEachIndexed
                    leituras++
                    if (v == null) { falhas++; valores[i] = null } else {
                        val s = comSinal(nums[i], v)
                        valores[i] = s
                        minimos[i] = minOf(minimos[i] ?: s, s); maximos[i] = maxOf(maximos[i] ?: s, s)
                    }
                    mostrar(i)
                }
                status.text = String.format(Locale.US, "4 leituras/s · erros %.0f%% · toque num quadro para escolher outro parâmetro",
                    if (leituras > 0) 100.0 * falhas / leituras else 0.0)
                if (leituras > 400) { leituras /= 2; falhas /= 2 }
            }
        }.start()
    }

    private fun comSinal(n: Int, v: Int): Int {
        val p = defs[n]
        return if ((p == null || p.assinado) && v > 32767) v - 65536 else v
    }

    private fun formata(n: Int, v: Int?): String {
        if (v == null) return "—"
        val p = defs[n]
        if (n == 680 || n == 682 || n == 12 || n == 13) return "0x%04X".format(v and 0xFFFF)
        p?.opcoes?.firstOrNull { it.first == v }?.let { return "$v ${it.second}" }
        val dec = p?.dec ?: 0
        val t = if (dec == 0) "$v" else String.format(Locale.US, "%.${dec}f", v / Math.pow(10.0, dec.toDouble())).replace('.', ',')
        return if (p?.unidade.isNullOrEmpty()) t else "$t ${p!!.unidade}"
    }

    private fun mostrar(i: Int) {
        val n = nums[i]
        txtNome[i]!!.text = "P%04d  %s".format(n, defs[n]?.nome ?: "")
        txtValor[i]!!.text = formata(n, valores[i])
        txtValor[i]!!.textSize = if (formata(n, valores[i]).length > 10) 18f else 30f
        txtMinMax[i]!!.text = if (minimos[i] == null) "" else "mín ${formata(n, minimos[i])} · máx ${formata(n, maximos[i])}"
    }

    private fun escolher(i: Int) {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText(nums[i].toString()); selectAll()
            hint = "número do parâmetro (ex.: 18)"
        }
        val sugestoes = (Parametros.somenteLeitura.map { it.num }).joinToString(", ")
        AlertDialog.Builder(this)
            .setTitle("Quadro ${i + 1}: qual parâmetro?")
            .setMessage("Dados ao vivo mais usados: $sugestoes")
            .setView(campo)
            .setPositiveButton("OK") { _, _ ->
                val n = campo.text.toString().toIntOrNull()
                if (n != null && n in 0..1999) {
                    nums[i] = n; valores[i] = null; minimos[i] = null; maximos[i] = null
                    prefs.edit().putInt("p$i", n).apply(); mostrar(i)
                }
            }
            .setNeutralButton("Padrão") { _, _ ->
                nums[i] = padrao[i]; valores[i] = null; minimos[i] = null; maximos[i] = null
                prefs.edit().putInt("p$i", padrao[i]).apply(); mostrar(i)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun botao(txt: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#2A2F36")) }
        setOnClickListener { acao() }
    }
}
