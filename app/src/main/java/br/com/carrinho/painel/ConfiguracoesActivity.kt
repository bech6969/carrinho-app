package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/**
 * Tela com todos os parametros configuraveis do CVW300.
 * Fluxo: "Ler do inversor" -> editar -> "Salvar" (grava so o que mudou e confere).
 */
class ConfiguracoesActivity : Activity() {

    private class Campo(
        val p: Param,
        val linha: LinearLayout,
        val titulo: TextView,
        val edit: EditText?,
        val spinner: Spinner?
    ) {
        var lido: Int? = null
    }

    private val campos = mutableListOf<Campo>()
    private val cabecalhos = mutableMapOf<String, TextView>()
    private lateinit var info: TextView
    private lateinit var btnLer: Button
    private lateinit var btnSalvar: Button
    private lateinit var btnPadrao: Button
    @Volatile private var ocupado = false

    private val corFundo = Color.parseColor("#0D0F12")
    private val corCard = Color.parseColor("#1B1F24")
    private val corMudou = Color.parseColor("#3A2F0B")
    private val corErro = Color.parseColor("#4A1414")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(corFundo)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // ---- Barra superior ----
        val barra = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val voltar = botao("← Painel", "#2A2F36") { finish() }
        btnLer = botao("Ler do inversor", "#1A73E8") { ler() }
        btnPadrao = botao("Valores padrão", "#2A2F36") { preencherPadrao() }
        btnSalvar = botao("Salvar", "#1E8E3E") { confirmarSalvar() }
        listOf(voltar, btnLer, btnPadrao, btnSalvar).forEachIndexed { i, b ->
            barra.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) marginStart = dp(10) })
        }
        raiz.addView(barra)

        val busca = EditText(this).apply {
            hint = "Buscar parâmetro (ex.: 134, rampa, bateria)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#80868B"))
            setSingleLine()
            background = cantos(corCard)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(simplesWatcher { filtrar(it) })
        }
        raiz.addView(busca, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        info = TextView(this).apply {
            setTextColor(Color.parseColor("#9AA0A6")); textSize = 15f
            text = "Toque em \"Ler do inversor\" para carregar os valores atuais."
        }
        raiz.addView(info, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        // ---- Lista de parametros ----
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var grupoAtual = ""
        for (p in Parametros.todos) {
            if (p.grupo != grupoAtual) {
                grupoAtual = p.grupo
                val h = TextView(this).apply {
                    text = p.grupo.uppercase(Locale.ROOT)
                    setTextColor(Color.parseColor("#8AB4F8")); textSize = 15f
                    setPadding(dp(4), dp(18), 0, dp(6))
                }
                cabecalhos[p.grupo] = h
                lista.addView(h)
            }
            lista.addView(criarLinha(p))
        }
        val scroll = ScrollView(this).apply { addView(lista) }
        raiz.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })

        setContentView(raiz)
        if (Conexao.conectado) ler()
        else info.text = "Sem conexão com o inversor. Volte ao painel e conecte o adaptador USB."
    }

    // ---------------- Montagem ----------------

    private fun criarLinha(p: Param): View {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cantos(corCard)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titulo = TextView(this).apply {
            text = "${p.codigo} · ${p.nome}" + if (p.unidade.isNotEmpty()) " (${p.unidade})" else ""
            setTextColor(Color.WHITE); textSize = 17f
        }
        val faixa = if (p.opcoes != null) "Padrão: ${rotuloOpcao(p, p.padrao.toIntOrNull()) ?: p.padrao}"
                    else "Faixa: ${formata(p, p.min)} a ${formata(p, p.max)} · Padrão: ${p.padrao}"
        val sub = TextView(this).apply {
            text = faixa
            setTextColor(Color.parseColor("#9AA0A6")); textSize = 13f
        }
        textos.addView(titulo); textos.addView(sub)
        p.alerta?.let {
            textos.addView(TextView(this).apply {
                text = "⚠ $it"; setTextColor(Color.parseColor("#F9AB00")); textSize = 13f
            })
        }
        linha.addView(textos, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.6f))

        var edit: EditText? = null
        var spinner: Spinner? = null
        val campo: Campo
        if (p.opcoes != null) {
            spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@ConfiguracoesActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("—") + p.opcoes.map { "${it.first} = ${it.second}" })
            }
            linha.addView(spinner, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            campo = Campo(p, linha, titulo, null, spinner)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) = marcaMudanca(campo)
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        } else {
            edit = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    (if (p.dec > 0) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or
                    (if (p.assinado) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                setTextColor(Color.WHITE); textSize = 20f
                gravity = Gravity.END
                setSelectAllOnFocus(true)
            }
            linha.addView(edit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            campo = Campo(p, linha, titulo, edit, null)
            edit.addTextChangedListener(simplesWatcher { marcaMudanca(campo) })
        }
        campos += campo
        return linha.also {
            it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        }
    }

    // ---------------- Valores ----------------

    private fun fator(dec: Int): Double { var f = 1.0; repeat(dec) { f *= 10 }; return f }

    private fun formata(p: Param, bruto: Int): String =
        if (p.dec == 0) bruto.toString()
        else String.format(Locale.US, "%.${p.dec}f", bruto / fator(p.dec))

    private fun rotuloOpcao(p: Param, v: Int?): String? =
        p.opcoes?.firstOrNull { it.first == v }?.let { "${it.first} = ${it.second}" }

    /** Valor bruto digitado/selecionado, ou null se vazio/invalido. */
    private fun valorCampo(c: Campo): Int? {
        c.spinner?.let { sp ->
            val pos = sp.selectedItemPosition
            return if (pos <= 0) null else c.p.opcoes!![pos - 1].first
        }
        val txt = c.edit!!.text.toString().trim().replace(',', '.')
        if (txt.isEmpty()) return null
        val d = txt.toDoubleOrNull() ?: return null
        return Math.round(d * fator(c.p.dec)).toInt()
    }

    private fun mostraValor(c: Campo, bruto: Int?) {
        if (c.spinner != null) {
            val idx = c.p.opcoes!!.indexOfFirst { it.first == bruto }
            c.spinner.setSelection(if (idx >= 0) idx + 1 else 0)
        } else {
            c.edit!!.setText(if (bruto == null) "" else formata(c.p, bruto))
        }
    }

    private fun valido(c: Campo, v: Int) = v in c.p.min..c.p.max &&
        (c.p.opcoes == null || c.p.opcoes.any { it.first == v })

    private fun mudou(c: Campo): Boolean {
        val v = valorCampo(c) ?: return false
        return v != c.lido
    }

    private fun marcaMudanca(c: Campo) {
        val v = valorCampo(c)
        val cor = when {
            v != null && !valido(c, v) -> corErro
            mudou(c) -> corMudou
            else -> corCard
        }
        c.linha.background = cantos(cor)
        atualizaContador()
    }

    private fun atualizaContador() {
        if (ocupado) return
        val n = campos.count { mudou(it) }
        btnSalvar.text = if (n > 0) "Salvar ($n)" else "Salvar"
    }

    private fun preencherPadrao() {
        AlertDialog.Builder(this)
            .setTitle("Preencher valores padrão?")
            .setMessage("Os campos serão preenchidos com o padrão de fábrica do manual. Nada é gravado até você tocar em Salvar. Parâmetros que dependem do modelo (×Inom) não são alterados.")
            .setPositiveButton("Preencher") { _, _ ->
                for (c in campos) {
                    val pad = c.p.padrao.replace(',', '.').toDoubleOrNull() ?: continue
                    mostraValor(c, Math.round(pad * fator(c.p.dec)).toInt())
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun filtrar(termo: String) {
        val t = termo.trim().lowercase(Locale.ROOT)
        val gruposVisiveis = mutableSetOf<String>()
        for (c in campos) {
            val ok = t.isEmpty() || c.p.codigo.lowercase(Locale.ROOT).contains(t) ||
                c.p.num.toString() == t || c.p.nome.lowercase(Locale.ROOT).contains(t) ||
                c.p.grupo.lowercase(Locale.ROOT).contains(t)
            c.linha.visibility = if (ok) View.VISIBLE else View.GONE
            if (ok) gruposVisiveis += c.p.grupo
        }
        cabecalhos.forEach { (g, v) -> v.visibility = if (g in gruposVisiveis) View.VISIBLE else View.GONE }
    }

    // ---------------- Leitura ----------------

    private fun ler() {
        val mb = Conexao.mb ?: return avisoInfo("Sem conexão com o inversor.")
        if (ocupado) return
        travar(true)
        Thread {
            var falhas = 0
            campos.forEachIndexed { i, c ->
                val r = mb.readHolding(c.p.num, 1)
                val v = r?.get(0)?.let { if (c.p.assinado) it.toShort().toInt() else it }
                if (v == null) falhas++
                runOnUiThread {
                    c.lido = v
                    mostraValor(c, v)
                    marcaMudanca(c)
                    info.text = "Lendo ${i + 1} de ${campos.size}…"
                }
            }
            runOnUiThread {
                travar(false)
                info.text = if (falhas == 0) "Valores lidos do inversor. Campos alterados ficam destacados."
                            else "Leitura concluída. $falhas parâmetro(s) não responderam (podem não existir na sua versão de firmware)."
                atualizaContador()
            }
        }.start()
    }

    // ---------------- Gravacao ----------------

    private fun confirmarSalvar() {
        if (ocupado) return
        if (!Conexao.conectado) return avisoInfo("Sem conexão com o inversor.")

        val invalidos = campos.filter { c -> valorCampo(c)?.let { !valido(c, it) } == true }
        if (invalidos.isNotEmpty()) {
            return dialogo("Valores fora da faixa",
                invalidos.joinToString("\n") { "${it.p.codigo} · ${it.p.nome}" })
        }
        val alterados = campos.filter { mudou(it) }
        if (alterados.isEmpty()) return avisoInfo("Nenhuma alteração para salvar.")
        if (!Conexao.parado) return dialogo("Carrinho em movimento", "Pare o carrinho para alterar parâmetros.")

        val resumo = alterados.joinToString("\n") { c ->
            val antes = c.lido?.let { if (c.p.opcoes != null) rotuloOpcao(c.p, it) ?: "$it" else formata(c.p, it) } ?: "?"
            val depois = valorCampo(c)!!.let { if (c.p.opcoes != null) rotuloOpcao(c.p, it) ?: "$it" else formata(c.p, it) }
            (if (c.p.alerta != null) "⚠ " else "") + "${c.p.codigo} ${c.p.nome}: $antes → $depois"
        }
        AlertDialog.Builder(this)
            .setTitle("Gravar ${alterados.size} parâmetro(s) no inversor?")
            .setMessage(resumo)
            .setPositiveButton("Gravar") { _, _ -> salvar(alterados) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvar(alterados: List<Campo>) {
        val mb = Conexao.mb ?: return avisoInfo("Sem conexão com o inversor.")
        travar(true)
        Thread {
            // Libera alteracao de parametros (P0000 = senha)
            mb.writeSingle(0, Config.SENHA_P0000)

            val erros = mutableListOf<String>()
            alterados.forEachIndexed { i, c ->
                runOnUiThread { info.text = "Gravando ${i + 1} de ${alterados.size}: ${c.p.codigo}…" }
                if (!Conexao.parado) { erros += "${c.p.codigo}: carrinho em movimento — interrompido"; return@forEachIndexed }
                val v = valorCampo(c)!!
                val ok = mb.writeSingle(c.p.num, v) &&
                    mb.readHolding(c.p.num, 1)?.get(0)?.let { (if (c.p.assinado) it.toShort().toInt() else it) == v } == true
                val antes = c.lido?.let { Backup.formata(c.p, it) } ?: "?"
                if (ok) {
                    Registro.evento("CONFIG", "${c.p.codigo} ${c.p.nome}: $antes → ${Backup.formata(c.p, v)}")
                    runOnUiThread { c.lido = v; marcaMudanca(c) }
                } else {
                    erros += "${c.p.codigo} · ${c.p.nome}"
                    Registro.evento("CONFIG", "FALHOU gravar ${c.p.codigo} ${c.p.nome} = ${Backup.formata(c.p, v)}")
                }
            }

            // Grava na memoria permanente (manual: P0303 = 1 apos qualquer alteracao)
            val salvouMemoria = erros.size < alterados.size && Conexao.parado &&
                mb.writeSingle(303, 1)
            Registro.evento("CONFIG", if (salvouMemoria) "Gravado na memória (P0303 = 1)" else "Não gravou na memória (P0303)")

            runOnUiThread {
                travar(false)
                atualizaContador()
                if (erros.isEmpty()) {
                    info.text = if (salvouMemoria)
                        "✔ ${alterados.size} parâmetro(s) gravado(s), conferido(s) e salvos na memória (P0303)."
                    else "⚠ Parâmetros gravados, mas falhou salvar na memória (P0303). Eles podem se perder ao desligar."
                    if (alterados.any { it.p.num == 400 })
                        dialogo("Desligue e ligue o inversor", "A nova tensão do motor (P0400) só passa a valer depois de desenergizar e energizar o inversor.")
                    if (alterados.any { it.p.num in listOf(308, 310, 311) })
                        dialogo("Comunicação alterada",
                            "Você mudou endereço, velocidade ou paridade da serial. Ajuste o Config.kt do app com os novos valores e reinstale, senão o painel não vai mais se comunicar.")
                } else {
                    info.text = "Algumas gravações falharam."
                    dialogo("Não foi possível gravar",
                        erros.joinToString("\n") +
                        "\n\nPossíveis causas: senha (P0000) incorreta, parâmetro só pode ser alterado com o inversor desabilitado, ou valor fora da faixa permitida pelo modelo.")
                }
            }
        }.start()
    }

    // ---------------- Utilitarios ----------------

    private fun travar(sim: Boolean) {
        ocupado = sim
        btnLer.isEnabled = !sim; btnSalvar.isEnabled = !sim; btnPadrao.isEnabled = !sim
        btnLer.alpha = if (sim) 0.5f else 1f; btnSalvar.alpha = btnLer.alpha; btnPadrao.alpha = btnLer.alpha
    }

    private fun avisoInfo(msg: String) { info.text = msg }

    private fun dialogo(titulo: String, msg: String) {
        AlertDialog.Builder(this).setTitle(titulo).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun cantos(cor: Int) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(cor)
    }

    private fun botao(txt: String, cor: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 17f
        setTextColor(Color.WHITE)
        background = cantos(Color.parseColor(cor))
        setOnClickListener { acao() }
    }

    private fun simplesWatcher(f: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = f(s?.toString() ?: "")
    }
}
