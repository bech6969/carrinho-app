package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Edita os perfis de conducao (ECO, NORMAL, SPORT...). Mostra o que esta no inversor agora,
 * permite alterar cada perfil, criar novos (inclusive a partir dos valores atuais) e excluir.
 * Salvar aqui guarda o perfil no app; o painel aplica no inversor ao escolher o perfil.
 */
class PerfisActivity : Activity() {

    private lateinit var perfis: MutableList<Perfil>
    private lateinit var lista: LinearLayout
    private lateinit var atualTxt: TextView
    private lateinit var info: TextView
    /** Valores lidos agora do inversor (numero -> bruto). */
    private val atuais = mutableMapOf<Int, Int>()

    private val corFundo = Color.parseColor("#0D0F12")
    private val corCard = Color.parseColor("#1B1F24")
    private val corCampo = Color.parseColor("#2A2F36")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        perfis = Perfis.carregar(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(corFundo)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val barra = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            botao("← Painel", "#2A2F36") { finish() },
            botao("Ler do inversor", "#1A73E8") { lerInversor() },
            botao("+ Novo perfil", "#1E8E3E") { novoPerfil(null) }
        ).forEachIndexed { i, b ->
            barra.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f).apply { if (i > 0) marginStart = dp(8) })
        }
        raiz.addView(barra)

        info = texto("", 14f, "#BDC1C6")
        raiz.addView(info, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(ScrollView(this).apply { addView(lista) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })
        setContentView(raiz)

        montar()
        if (Conexao.conectado) lerInversor()
        else info.text = "Sem conexão com o inversor: dá para editar os perfis, mas não para ver os valores atuais."
    }

    // ---------------- Tela ----------------

    private fun montar() {
        lista.removeAllViews()

        // Card "Agora no inversor"
        val cardAtual = card()
        cardAtual.addView(texto("AGORA NO INVERSOR", 15f, "#8AB4F8", negrito = true))
        atualTxt = texto(textoAtual(), 16f, "#FFFFFF")
        cardAtual.addView(atualTxt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
        cardAtual.addView(botao("Salvar estes valores como novo perfil", "#1A73E8") {
            if (atuais.isEmpty()) info.text = "Leia os valores do inversor primeiro."
            else novoPerfil(atuais.toMutableMap())
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        lista.addView(cardAtual, margem())

        val ativo = Perfis.ativo(this)
        perfis.forEachIndexed { i, p -> lista.addView(cardPerfil(i, p, i == ativo), margem()) }
    }

    private fun textoAtual(): String =
        if (atuais.isEmpty()) "—"
        else Perfis.campos.joinToString("\n") { c ->
            "${c.codigo} ${c.nome}: " + (atuais[c.num]?.let { "${Perfis.paraTexto(c, it)} ${c.unidade}" } ?: "sem resposta")
        }

    private fun cardPerfil(indice: Int, p: Perfil, ativo: Boolean): LinearLayout {
        val c = card()
        if (ativo) c.background = cantos(Color.parseColor("#2B1F3D"))

        val nome = EditText(this).apply {
            setText(p.nome); setSingleLine(); textSize = 20f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE); background = cantos(corCampo)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topo.addView(nome, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (ativo) topo.addView(texto("  ● em uso", 15f, "#C58AF9", negrito = true))
        c.addView(topo)

        val edits = mutableMapOf<Int, EditText>()
        for (campo in Perfis.campos) {
            val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val atual = atuais[campo.num]?.let { "  (agora ${Perfis.paraTexto(campo, it)})" } ?: ""
            linha.addView(texto("${campo.codigo} ${campo.nome}$atual", 15f, "#E8EAED"),
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.6f))
            val e = EditText(this).apply {
                setText(p.valores[campo.num]?.let { Perfis.paraTexto(campo, it) } ?: "")
                hint = "não altera"; setHintTextColor(Color.parseColor("#80868B"))
                setSingleLine(); textSize = 18f; gravity = Gravity.END
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE); background = cantos(corCampo)
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            edits[campo.num] = e
            linha.addView(e, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            linha.addView(texto(" ${campo.unidade}", 15f, "#BDC1C6"), LinearLayout.LayoutParams(dp(44), WRAP_CONTENT))
            c.addView(linha, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        val botoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            botao("Salvar", "#1E8E3E") { salvarPerfil(indice, nome, edits) },
            botao("Copiar do inversor", "#2A2F36") {
                if (atuais.isEmpty()) info.text = "Leia os valores do inversor primeiro."
                else edits.forEach { (n, e) -> atuais[n]?.let { v -> e.setText(Perfis.paraTexto(Perfis.campos.first { it.num == n }, v)) } }
            },
            botao("Excluir", "#5C1A1A") { excluir(indice) }
        ).forEachIndexed { i, b ->
            botoes.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i > 0) marginStart = dp(8) })
        }
        c.addView(botoes, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        return c
    }

    // ---------------- Acoes ----------------

    private fun salvarPerfil(indice: Int, nomeEd: EditText, edits: Map<Int, EditText>) {
        val nome = nomeEd.text.toString().trim()
        if (nome.isEmpty()) { info.text = "Dê um nome ao perfil."; return }
        val novos = mutableMapOf<Int, Int>()
        for (campo in Perfis.campos) {
            val txt = edits[campo.num]!!.text.toString()
            if (txt.isBlank()) continue
            val v = Perfis.paraBruto(campo, txt)
            if (v == null || v < campo.min || v > campo.max) {
                dialogo("Valor inválido", "${campo.codigo} ${campo.nome}: use de " +
                    "${Perfis.paraTexto(campo, campo.min)} a ${Perfis.paraTexto(campo, campo.max)} ${campo.unidade}.")
                return
            }
            novos[campo.num] = v
        }
        val antigo = perfis[indice]
        perfis[indice] = Perfil(nome, novos)
        Perfis.salvar(this, perfis)
        Registro.evento("PERFIL", "Perfil ${antigo.nome} salvo como $nome: ${Perfis.resumo(perfis[indice])}")
        info.text = "✔ Perfil $nome salvo." +
            if (indice == Perfis.ativo(this)) " Ele está em uso: o painel aplica os novos valores ao voltar." else ""
        montar()
    }

    private fun novoPerfil(valores: MutableMap<Int, Int>?) {
        if (perfis.size >= Perfis.MAX_PERFIS) { info.text = "Máximo de ${Perfis.MAX_PERFIS} perfis. Exclua um antes."; return }
        val ed = EditText(this).apply { hint = "Nome (ex.: CHUVA, CRIANÇA)"; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("Novo perfil")
            .setView(ed)
            .setPositiveButton("Criar") { _, _ ->
                val nome = ed.text.toString().trim().ifEmpty { "PERFIL ${perfis.size + 1}" }
                val base = valores ?: perfis.getOrNull(Perfis.ativo(this))?.valores?.toMutableMap() ?: mutableMapOf()
                perfis.add(Perfil(nome, base))
                Perfis.salvar(this, perfis)
                Registro.evento("PERFIL", "Perfil $nome criado: ${Perfis.resumo(perfis.last())}")
                info.text = "✔ Perfil $nome criado. Ajuste os valores e toque em Salvar."
                montar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluir(indice: Int) {
        if (perfis.size <= 1) { info.text = "Precisa ficar pelo menos um perfil."; return }
        val p = perfis[indice]
        AlertDialog.Builder(this)
            .setTitle("Excluir o perfil ${p.nome}?")
            .setPositiveButton("Excluir") { _, _ ->
                perfis.removeAt(indice)
                Perfis.salvar(this, perfis)
                val ativo = Perfis.ativo(this)
                if (ativo == indice) Perfis.setAtivo(this, 0)
                else if (ativo > indice) Perfis.setAtivo(this, ativo - 1)
                Registro.evento("PERFIL", "Perfil ${p.nome} excluído")
                info.text = "Perfil ${p.nome} excluído."
                montar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun lerInversor() {
        val mb = Conexao.mb ?: run { info.text = "Sem conexão com o inversor."; return }
        info.text = "Lendo do inversor…"
        Thread {
            val lidos = mutableMapOf<Int, Int>()
            for (c in Perfis.campos) mb.readHolding(c.num, 1)?.let { r ->
                lidos[c.num] = if (c.assinado) r[0].toShort().toInt() else r[0]
            }
            runOnUiThread {
                atuais.clear(); atuais.putAll(lidos)
                info.text = if (lidos.size == Perfis.campos.size) "Valores atuais lidos do inversor."
                            else "Leitura incompleta: ${Perfis.campos.size - lidos.size} parâmetro(s) sem resposta."
                montar()
            }
        }.start()
    }

    // ---------------- Utilitarios ----------------

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cantos(corCard)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun margem() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }

    private fun texto(t: String, tam: Float, cor: String, negrito: Boolean = false) = TextView(this).apply {
        text = t; textSize = tam; setTextColor(Color.parseColor(cor))
        if (negrito) setTypeface(null, Typeface.BOLD)
    }

    private fun cantos(cor: Int) = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(cor) }

    private fun botao(txt: String, cor: String, acao: () -> Unit) = Button(this).apply {
        text = txt; isAllCaps = false; textSize = 15f
        setTextColor(Color.WHITE)
        background = cantos(Color.parseColor(cor))
        setOnClickListener { acao() }
    }

    private fun dialogo(titulo: String, msg: String) {
        AlertDialog.Builder(this).setTitle(titulo).setMessage(msg).setPositiveButton("OK", null).show()
    }
}
