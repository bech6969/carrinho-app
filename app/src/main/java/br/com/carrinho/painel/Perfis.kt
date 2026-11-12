package br.com.carrinho.painel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Perfil de conducao (ECO, NORMAL, SPORT ou criados pelo usuario).
 * [valores]: numero do parametro -> valor bruto (x 10^dec). Parametro ausente = o perfil nao altera.
 * Ao escolher o perfil no painel, os valores sao escritos so na RAM do inversor (sem P0303).
 */
data class Perfil(var nome: String, val valores: MutableMap<Int, Int> = mutableMapOf())

object Perfis {

    /** Parametros que um perfil pode alterar. Para incluir outro, acrescente o numero aqui. */
    private val NUMEROS = listOf(134, 100, 101, 169)
    val campos: List<Param> by lazy { NUMEROS.mapNotNull { n -> Parametros.todos.firstOrNull { it.num == n } } }

    const val MAX_PERFIS = 6
    private const val PREFS = "perfis"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun carregar(c: Context): MutableList<Perfil> {
        val json = prefs(c).getString("lista", null) ?: return padrao()
        return try {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val v = o.getJSONObject("valores")
                Perfil(o.getString("nome"), v.keys().asSequence().associate { it.toInt() to v.getInt(it) }.toMutableMap())
            }.ifEmpty { padrao() }
        } catch (_: Exception) { padrao() }
    }

    fun salvar(c: Context, lista: List<Perfil>) {
        val arr = JSONArray()
        lista.forEach { p ->
            val v = JSONObject(); p.valores.forEach { (k, x) -> v.put(k.toString(), x) }
            arr.put(JSONObject().put("nome", p.nome).put("valores", v))
        }
        prefs(c).edit().putString("lista", arr.toString()).apply()
    }

    fun ativo(c: Context) = prefs(c).getInt("ativo", 1)
    fun setAtivo(c: Context, i: Int) = prefs(c).edit().putInt("ativo", i).apply()

    private fun padrao() = Config.NOME_MODO.indices.map { i ->
        Perfil(Config.NOME_MODO[i], mutableMapOf(Config.R_VELMAX to Config.VEL_MAX_MODO[i]))
    }.toMutableList()

    /** Texto digitado ("12,5") -> valor bruto. null = vazio ou invalido. */
    fun paraBruto(p: Param, txt: String): Int? {
        val d = txt.trim().replace(',', '.').toDoubleOrNull() ?: return null
        var f = 1.0; repeat(p.dec) { f *= 10 }
        return (d * f).roundToInt()
    }

    /** Valor bruto -> texto para mostrar/editar ("12,5"). */
    fun paraTexto(p: Param, bruto: Int): String {
        if (p.dec == 0) return bruto.toString()
        var f = 1.0; repeat(p.dec) { f *= 10 }
        return String.format("%.${p.dec}f", bruto / f).replace('.', ',')
    }

    fun resumo(perfil: Perfil): String = campos.filter { it.num in perfil.valores }
        .joinToString("  ·  ") { "${it.nome}: ${paraTexto(it, perfil.valores[it.num]!!)} ${it.unidade}".trim() }
        .ifEmpty { "não altera nada" }
}
