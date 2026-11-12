package br.com.carrinho.painel

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

/**
 * Envio automatico de logs e backups para um repositorio PRIVADO do GitHub ([Config.REPO_LOGS]),
 * pela API de conteudo. Cada arquivo vai compactado (.csv.gz) e so e reenviado quando muda.
 * O token (fine-grained, so esse repositorio, Contents: Read and write) fica salvo no tablet,
 * nunca no codigo.
 */
object Envio {

    private const val PREFS = "envio"
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile var ultimoStatus = "Envio automático desligado (sem token)."
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun token(c: Context): String? = prefs(c).getString("token", null)?.takeIf { it.isNotBlank() }
    fun setToken(c: Context, t: String?) {
        val e = prefs(c).edit()
        if (t.isNullOrBlank()) e.remove("token") else e.putString("token", t.trim())
        e.apply()
        ultimoStatus = if (t.isNullOrBlank()) "Envio automático desligado (sem token)." else "Token salvo."
    }

    /** Envia em segundo plano o que mudou. [aoTerminar] roda na thread de envio. */
    fun enviarTudo(c: Context, aoTerminar: ((String) -> Unit)? = null) {
        val ctx = c.applicationContext
        exec.execute {
            val r = try { enviar(ctx) } catch (e: Exception) { "Erro no envio: ${e.message}" }
            ultimoStatus = r
            aoTerminar?.invoke(r)
        }
    }

    private fun enviar(c: Context): String {
        val tok = token(c) ?: return "Envio automático desligado (sem token)."
        val p = prefs(c)
        val arquivos = Backup.lista(c).map { "backups/" to it } +
            (Registro.pastaLogs().listFiles { f -> f.name.endsWith(".csv") }?.map { "logs/" to it } ?: emptyList())
        var enviados = 0; var erros = 0
        for ((pasta, f) in arquivos) {
            val caminho = pasta + f.name + ".gz"
            val marca = "${f.length()}_${f.lastModified()}"
            if (p.getString("m_$caminho", null) == marca) continue
            val conteudo = Base64.encodeToString(gzip(f), Base64.NO_WRAP)
            var sha = p.getString("s_$caminho", null)
            var (cod, novoSha) = put(tok, caminho, conteudo, sha)
            if (cod == 409 || cod == 422) {           // sha desconhecido/antigo: busca e tenta de novo
                sha = shaRemoto(tok, caminho)
                val r = put(tok, caminho, conteudo, sha); cod = r.first; novoSha = r.second
            }
            when {
                cod == 401 || cod == 403 -> return "Token sem permissão ou vencido (HTTP $cod). Gere outro."
                cod == 404 -> return "Repositório ${Config.REPO_LOGS} não encontrado (ou token sem acesso a ele)."
                (cod == 200 || cod == 201) && novoSha != null -> {
                    p.edit().putString("m_$caminho", marca).putString("s_$caminho", novoSha).apply()
                    enviados++
                }
                else -> erros++
            }
        }
        val hora = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        return when {
            erros > 0 -> "Envio $hora: $enviados enviado(s), $erros com erro (sem internet?)."
            enviados == 0 -> "Envio $hora: tudo já estava enviado."
            else -> "✔ Envio $hora: $enviados arquivo(s) enviado(s)."
        }
    }

    private fun gzip(f: File): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz -> f.inputStream().use { it.copyTo(gz) } }
        return bos.toByteArray()
    }

    private fun conexao(tok: String, caminho: String, metodo: String) =
        (URL("https://api.github.com/repos/${Config.REPO_LOGS}/contents/$caminho").openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $tok")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private fun put(tok: String, caminho: String, base64: String, sha: String?): Pair<Int, String?> {
        val corpo = JSONObject().put("message", "app: $caminho").put("content", base64)
        if (sha != null) corpo.put("sha", sha)
        val con = conexao(tok, caminho, "PUT")
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        con.outputStream.use { it.write(corpo.toString().toByteArray()) }
        val cod = con.responseCode
        val novoSha = if (cod == 200 || cod == 201) try {
            JSONObject(con.inputStream.bufferedReader().readText()).getJSONObject("content").getString("sha")
        } catch (_: Exception) { null } else null
        con.disconnect()
        return cod to novoSha
    }

    private fun shaRemoto(tok: String, caminho: String): String? {
        val con = conexao(tok, caminho, "GET")
        return try {
            if (con.responseCode != 200) null
            else JSONObject(con.inputStream.bufferedReader().readText()).getString("sha")
        } catch (_: Exception) { null } finally { con.disconnect() }
    }
}
