package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
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
 * Curva suave do pedal pela SoftPLC do inversor.
 *  - liga/desliga o programa (P1001) e mostra o estado (P1000) e o tempo de ciclo (P1002);
 *  - troca quem manda na referencia: pedal direto (como antes) ou SoftPLC (P0231=3, P0221=P0222=7);
 *  - ajusta P1010..P1015 com botoes - / + e presets;
 *  - salva na memoria (P0000 = 5, P0303 = 1).
 * Toda escrita exige o carrinho parado.
 */
class SoftplcActivity : Activity() {

    private class Ajuste(val num: Int, val nome: String, val dica: String, val dec: Int, val un: String,
                         val passo: Int, val min: Int, val max: Int, val padrao: Int) {
        var lido: Int? = null
        var valor: Int = padrao
        lateinit var txt: TextView
    }

    private val ajustes = listOf(
        Ajuste(1010, "Zona morta do pedal", "quanto do começo do pedal é ignorado", 1, "%", 5, 0, 300, 50),
        Ajuste(1011, "Curva (expoente)", "1 = reta · 2 = começo suave · 3 = bem suave", 1, "", 1, 10, 30, 20),
        Ajuste(1012, "Filtro do pedal", "amacia mexidas rápidas do pé", 0, "ms", 10, 0, 2000, 150),
        Ajuste(1013, "Aceleração máxima", "menor = arranca mais devagar", 0, "rpm/s", 50, 100, 3000, 800),
        Ajuste(1014, "Freio ao soltar", "maior = para mais rápido ao tirar o pé", 0, "rpm/s", 50, 100, 3000, 1200),
        Ajuste(1015, "Suavidade do tranco", "menor = início e fim mais macios", 0, "rpm/s²", 100, 200, 10000, 1500),
    )
    private val presets = linkedMapOf(
        "🐢 Muito suave" to intArrayOf(60, 25, 250, 500, 1000, 800),
        "⭐ Padrão" to intArrayOf(50, 20, 150, 800, 1200, 1500),
        "🐇 Mais esperto" to intArrayOf(30, 15, 100, 1200, 1500, 2500),
    )

    // parametros trocados ao passar para a SoftPLC (valor bruto); originais guardados para voltar
    private val modoPlc = linkedMapOf(231 to 3, 221 to 7, 222 to 7, 100 to 10, 101 to 10, 104 to 1)
    private val prefs by lazy { getSharedPreferences("softplc", MODE_PRIVATE) }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var aoVivo: TextView
    private lateinit var info: TextView
    private lateinit var btDireto: Button
    private lateinit var btPlc: Button
    private lateinit var btRodar: Button
    @Volatile private var ocupado = false
    private var p1000: Int? = null
    private var p1001: Int? = null
    private var p1002: Int? = null
    private var p231: Int? = null
    private var p222: Int? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(a: Ajuste, v: Int?) = when {
        v == null -> "?"
        a.dec == 0 -> "$v"
        else -> String.format(Locale.US, "%.1f", v / 10.0).replace('.', ',')
    }

    private val tick = object : Runnable {
        override fun run() { lerEstado(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0F12"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topo.addView(botao("← Voltar", "#2A2F36") { finish() }, LinearLayout.LayoutParams(dp(120), dp(46)))
        topo.addView(texto("  CURVA DO PEDAL · SoftPLC", 17f, "#8AB4F8", true))
        raiz.addView(topo)

        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- estado ----
        status = texto("Lendo…", 17f, "#FFFFFF", true)
        aoVivo = texto("", 16f, "#E8EAED").apply { typeface = Typeface.MONOSPACE }
        lista.addView(card(status, aoVivo), lp(8))

        // ---- quem manda ----
        lista.addView(titulo("1. QUEM COMANDA O PEDAL"))
        val linhaModo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btDireto = botao("Pedal direto\n(como era antes)", "#2A2F36") { trocarModo(false) }
        btPlc = botao("Curva suave\n(SoftPLC)", "#2A2F36") { trocarModo(true) }
        linhaModo.addView(btDireto, LinearLayout.LayoutParams(0, dp(70), 1f))
        linhaModo.addView(btPlc, LinearLayout.LayoutParams(0, dp(70), 1f).apply { marginStart = dp(8) })
        lista.addView(linhaModo, lp(4))

        // ---- programa ----
        lista.addView(titulo("2. PROGRAMA NO INVERSOR"))
        btRodar = botao("…", "#2A2F36") { rodar(p1001 != 1) }
        lista.addView(btRodar, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(4) })

        // ---- ajustes ----
        lista.addView(titulo("3. AJUSTE DA CURVA"))
        val linhaPre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presets.entries.forEachIndexed { i, (nome, v) ->
            linhaPre.addView(botao(nome, "#3C4148") {
                ajustes.forEachIndexed { k, a -> a.valor = v[k] }; mostrarAjustes()
                info.text = "$nome preenchido. Toque em \"Gravar ajustes\" para mandar ao inversor."
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if (i > 0) marginStart = dp(6) })
        }
        lista.addView(linhaPre, lp(4))
        for (a in ajustes) lista.addView(linhaAjuste(a), lp(6))
        val linhaGrava = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        linhaGrava.addView(botao("Gravar ajustes", "#1A73E8") { gravarAjustes() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        linhaGrava.addView(botao("Desfazer", "#2A2F36") { ajustes.forEach { a -> a.lido?.let { a.valor = it } }; mostrarAjustes() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(8) })
        lista.addView(linhaGrava, lp(8))

        // ---- salvar ----
        lista.addView(titulo("4. QUANDO ESTIVER BOM"))
        lista.addView(botao("💾 Salvar tudo na memória do inversor", "#1E8E3E") { salvarMemoria() },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(4) })

        info = texto("", 15f, "#F9AB00")
        lista.addView(info, lp(10))
        lista.addView(texto(
            "Tudo que é gravado aqui vai só para a RAM até tocar em Salvar. Se desligar o inversor sem salvar, volta o que estava.\n" +
            "Para mexer: chave em N e carrinho parado. Teste no chão em lugar aberto, devagar.\n" +
            "Começo \"morto\" → baixe a zona morta ou a curva. Brusco → baixe a suavidade ou a aceleração. Rola demais ao soltar → suba o freio.",
            13f, "#9AA0A6"), lp(10))

        raiz.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        lerAjustes()
    }

    override fun onResume() { super.onResume(); Conexao.diagAtivo = true; handler.post(tick) }
    override fun onPause() { super.onPause(); Conexao.diagAtivo = false; handler.removeCallbacks(tick) }

    // ---------------- leitura ----------------

    private fun lerEstado() {
        val mb = Conexao.mb
        if (mb == null) { status.text = "Sem conexão com o inversor"; aoVivo.text = ""; return }
        if (!ocupado) Thread {
            val b = mb.readHolding(1000, 3)
            val m = mb.readHolding(231, 1)?.get(0)
            val r = mb.readHolding(222, 1)?.get(0)
            runOnUiThread {
                if (b != null) { p1000 = b[0]; p1001 = b[1]; p1002 = b[2] }
                if (m != null) p231 = m
                if (r != null) p222 = r
                mostrarEstado()
            }
        }.start()
        aoVivo.text = String.format(Locale.US, "Pedal %5.1f %%   Referência %5d rpm   Rotação %5d rpm",
            Conexao.acelerador, Conexao.refRpm, kotlin.math.abs(Conexao.rpm))
    }

    private fun mostrarEstado() {
        val est = when (p1000) {
            0 -> "sem programa"; 1 -> "instalando"; 2 -> "programa incompatível"; 3 -> "parado"; 4 -> "RODANDO"
            null -> "?"; else -> "estado $p1000"
        }
        val plc = p231 == 3 && p222 == 7
        status.text = "Programa: $est" + (p1002?.let { String.format(Locale.US, "  (ciclo %.1f ms)", it / 10.0) } ?: "") +
            "\nPedal: " + if (plc) "CURVA SUAVE (SoftPLC)" else if (p231 == null) "?" else "DIRETO (P0231=$p231, P0222=$p222)"
        status.setTextColor(Color.parseColor(if (plc && p1000 == 4) "#34C759" else if (plc) "#FF5252" else "#FFFFFF"))
        if (plc && p1000 != 4) status.append("\n⚠ Pedal na SoftPLC mas o programa não está rodando: o carrinho não anda.")
        pinta(btDireto, !plc && p231 != null, "#1A73E8")
        pinta(btPlc, plc, "#8430CE")
        btRodar.text = if (p1001 == 1) "⏹ Parar o programa (P1001 = 0)" else "▶ Rodar o programa (P1003 = 0, P1001 = 1)"
        pinta(btRodar, p1001 == 1, "#B06000")
    }

    private fun lerAjustes() {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão: mostrando os valores padrão."; mostrarAjustes(); return }
        Thread {
            val r = mb.readHolding(1010, 6)
            runOnUiThread {
                if (r != null) ajustes.forEachIndexed { k, a -> a.lido = r[k]; a.valor = if (r[k] in a.min..a.max) r[k] else a.padrao }
                else info.text = "Não consegui ler P1010..P1015 (programa instalado?). Mostrando os valores padrão."
                mostrarAjustes()
            }
        }.start()
    }

    private fun mostrarAjustes() {
        for (a in ajustes) {
            val mudou = a.lido != a.valor
            a.txt.text = fmt(a, a.valor) + (if (a.un.isNotEmpty()) " ${a.un}" else "") + if (mudou) "  •" else ""
            a.txt.setTextColor(Color.parseColor(if (mudou) "#F9AB00" else "#FFFFFF"))
        }
    }

    // ---------------- escrita ----------------

    private fun podeMexer(): ModbusRtu? {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão com o inversor."; return null }
        if (ocupado) return null
        if (!Conexao.parado) { info.text = "Ponha a chave em N e espere o carrinho parar."; return null }
        return mb
    }

    private fun escreve(mb: ModbusRtu, n: Int, v: Int): Boolean {
        mb.writeSingle(0, Config.SENHA_P0000)
        val ok = mb.writeSingle(n, v) && mb.readHolding(n, 1)?.get(0) == (v and 0xFFFF)
        Registro.evento("SOFTPLC", "P%04d = %d %s".format(n, v, if (ok) "OK" else "FALHOU"))
        return ok
    }

    private fun emSegundoPlano(msg: String, trabalho: (ModbusRtu) -> String) {
        val mb = podeMexer() ?: return
        ocupado = true; info.text = msg
        Thread {
            val r = try { trabalho(mb) } catch (e: Exception) { "Erro: ${e.message}" }
            runOnUiThread { ocupado = false; info.text = r; lerEstado(); mostrarAjustes() }
        }.start()
    }

    private fun trocarModo(plc: Boolean) {
        if (plc && p1000 != 4) {
            info.text = "Primeiro ponha o programa para rodar (passo 2). Sem ele, o carrinho não anda com o pedal na SoftPLC."
            return
        }
        val titulo = if (plc) "Passar o pedal para a curva suave?" else "Voltar o pedal direto (como era)?"
        val msg = if (plc) "Grava P0231 = 3, P0221 = P0222 = 7, rampas P0100 = P0101 = 1,0 s e curva S.\nOs valores de agora ficam guardados para voltar."
                  else "Volta P0231, P0221, P0222, P0100, P0101 e P0104 para o que estava antes da curva."
        confirma(titulo, msg) {
            emSegundoPlano("Gravando…") { mb ->
                if (plc) {
                    if (!prefs.contains("orig_231")) {          // guarda os originais uma unica vez
                        val ed = prefs.edit()
                        for (n in modoPlc.keys) {
                            val v = mb.readHolding(n, 1)?.get(0) ?: return@emSegundoPlano "Não consegui ler P%04d. Nada foi alterado.".format(n)
                            ed.putInt("orig_$n", v)
                        }
                        ed.apply()
                    }
                    val falhou = modoPlc.filter { (n, v) -> !escreve(mb, n, v) }.keys
                    if (falhou.isEmpty()) "✔ Pedal na curva suave. Teste: chave em N com pedal solto (não pode girar), depois F e encoste devagar."
                    else "⚠ Falhou: " + falhou.joinToString { "P%04d".format(it) } + ". Volte para \"Pedal direto\"."
                } else {
                    val orig = modoPlc.keys.associateWith { n -> prefs.getInt("orig_$n", if (n == 231) 0 else if (n == 222) 1 else -1) }
                        .filterValues { it >= 0 }
                    val falhou = orig.filter { (n, v) -> !escreve(mb, n, v) }.keys
                    if (falhou.isEmpty()) "✔ Pedal direto de novo." else "⚠ Falhou: " + falhou.joinToString { "P%04d".format(it) }
                }
            }
        }
    }

    private fun rodar(liga: Boolean) {
        val plc = p231 == 3 && p222 == 7
        if (!liga && plc) {
            info.text = "O pedal está na SoftPLC: volte para \"Pedal direto\" antes de parar o programa."
            return
        }
        emSegundoPlano(if (liga) "Ligando o programa…" else "Parando o programa…") { mb ->
            if (liga && !escreve(mb, 1003, 0)) return@emSegundoPlano "O inversor não aceitou P1003 = 0 (Usuário)."
            if (escreve(mb, 1001, if (liga) 1 else 0)) (if (liga) "✔ Programa rodando." else "Programa parado.")
            else "O inversor não aceitou P1001. O programa está instalado?"
        }
    }

    private fun gravarAjustes() {
        val mudados = ajustes.filter { it.lido != it.valor }
        if (mudados.isEmpty()) { info.text = "Nada mudou."; return }
        emSegundoPlano("Gravando ${mudados.size} ajuste(s)…") { mb ->
            val falhou = mudados.filter { a -> if (escreve(mb, a.num, a.valor)) { a.lido = a.valor; false } else true }
            if (falhou.isEmpty()) "✔ Ajustes gravados (só RAM). Teste e, se ficar bom, salve no passo 4."
            else "⚠ Não aceitou: " + falhou.joinToString { "P${it.num}" }
        }
    }

    private fun salvarMemoria() {
        confirma("Salvar tudo na memória?", "Grava P0303 = 1: o que está no inversor agora passa a valer mesmo depois de desligar.") {
            emSegundoPlano("Salvando…") { mb ->
                mb.writeSingle(0, Config.SENHA_P0000)
                val ok = mb.writeSingle(303, 1)
                Registro.evento("SOFTPLC", "P0303 = 1 " + if (ok) "OK" else "FALHOU")
                if (ok) "✔ Salvo na memória do inversor." else "Falhou gravar P0303."
            }
        }
    }

    // ---------------- tela ----------------

    private fun linhaAjuste(a: Ajuste): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(texto("${a.nome}  (P${a.num})", 15f, "#E8EAED", true))
        col.addView(texto(a.dica, 12f, "#9AA0A6"))
        a.txt = texto("", 20f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }
        fun muda(d: Int) { a.valor = (a.valor + d).coerceIn(a.min, a.max); mostrarAjustes() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B1F24")) }
            setPadding(dp(12), dp(8), dp(8), dp(8))
            addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(botao("−", "#3C4148") { muda(-a.passo) }, LinearLayout.LayoutParams(dp(56), dp(52)))
            addView(a.txt, LinearLayout.LayoutParams(dp(150), WRAP_CONTENT))
            addView(botao("+", "#3C4148") { muda(a.passo) }, LinearLayout.LayoutParams(dp(56), dp(52)))
        }
    }

    private fun confirma(titulo: String, msg: String, acao: () -> Unit) {
        if (podeMexer() == null) return
        AlertDialog.Builder(this).setTitle(titulo).setMessage(msg)
            .setPositiveButton("Confirmar") { _, _ -> acao() }.setNegativeButton("Cancelar", null).show()
    }

    private fun pinta(b: Button, ativo: Boolean, cor: String) {
        (b.background as GradientDrawable).setColor(Color.parseColor(if (ativo) cor else "#2A2F36"))
    }

    private fun titulo(t: String) = texto(t, 13f, "#8AB4F8", true).apply { setPadding(0, dp(14), 0, 0) }

    private fun lp(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun texto(t: String, tam: Float, cor: String, negrito: Boolean = false) = TextView(this).apply {
        text = t; textSize = tam; setTextColor(Color.parseColor(cor)); if (negrito) setTypeface(null, Typeface.BOLD)
    }

    private fun card(vararg v: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B1F24")) }
        setPadding(dp(12), dp(10), dp(12), dp(10)); v.forEach { addView(it) }
    }

    private fun botao(txt: String, cor: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor(cor)) }
        setOnClickListener { acao() }
    }
}
