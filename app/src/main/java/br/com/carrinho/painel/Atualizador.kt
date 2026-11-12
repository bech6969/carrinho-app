package br.com.carrinho.painel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualizacao automatica pelo GitHub Releases do repositorio do app.
 * Cada build do GitHub Actions publica uma release com Carrinho.apk + versao.json.
 */
object Atualizador {

    private const val BASE = "https://github.com/${Config.GITHUB_REPO}/releases/latest/download"
    private const val URL_VERSAO = "$BASE/versao.json"
    private const val URL_APK = "$BASE/Carrinho.apk"

    data class Versao(val codigo: Int, val nome: String, val notas: String)

    fun versaoInstalada(a: Activity): Int {
        val info = a.packageManager.getPackageInfo(a.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }

    fun nomeInstalado(a: Activity): String =
        a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "?"

    private fun abrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }

    /** Consulta a ultima versao publicada (rede, chamar fora da UI). */
    fun consultar(): Versao? = try {
        val c = abrir(URL_VERSAO)
        if (c.responseCode != 200) null
        else JSONObject(c.inputStream.bufferedReader().readText()).let {
            Versao(it.getInt("versionCode"), it.optString("versionName"), it.optString("notas"))
        }
    } catch (_: Exception) { null }

    /**
     * Verifica e, se houver versao nova, pergunta e instala.
     * [silencioso] = true nao mostra nada quando ja esta atualizado ou sem internet.
     */
    fun verificar(a: Activity, silencioso: Boolean, status: (String) -> Unit = {}) {
        Thread {
            val v = consultar()
            val atual = versaoInstalada(a)
            a.runOnUiThread {
                if (a.isFinishing) return@runOnUiThread
                when {
                    v == null -> if (!silencioso) status("Não foi possível verificar (sem internet?).")
                    v.codigo <= atual -> if (!silencioso) status("App atualizado (versão ${nomeInstalado(a)}).")
                    else -> perguntar(a, v, status)
                }
            }
        }.start()
    }

    private fun perguntar(a: Activity, v: Versao, status: (String) -> Unit) {
        AlertDialog.Builder(a)
            .setTitle("Nova versão ${v.nome} disponível")
            .setMessage((if (v.notas.isNotBlank()) "${v.notas}\n\n" else "") +
                "Versão instalada: ${nomeInstalado(a)}\n\nO carrinho precisa estar parado: durante a instalação o app fecha e o inversor para pelo watchdog.")
            .setPositiveButton("Atualizar") { _, _ -> baixarEInstalar(a, status) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun baixarEInstalar(a: Activity, status: (String) -> Unit) {
        if (!Conexao.parado) { status("Pare o carrinho para atualizar."); return }
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            status("Permita instalar apps do Carrinho e toque em Atualizar de novo.")
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        Registro.evento("ATUALIZACAO", "Baixando nova versão")
        Thread {
            try {
                val pasta = File(a.cacheDir, "atualizacao").apply { mkdirs() }
                val arq = File(pasta, "Carrinho.apk")
                val c = abrir(URL_APK)
                val total = c.contentLength
                c.inputStream.use { ent ->
                    arq.outputStream().use { sai ->
                        val buf = ByteArray(32 * 1024); var lido = 0L; var n: Int
                        while (ent.read(buf).also { n = it } > 0) {
                            sai.write(buf, 0, n); lido += n
                            if (total > 0) a.runOnUiThread { status("Baixando atualização… ${lido * 100 / total}%") }
                        }
                    }
                }
                Registro.evento("ATUALIZACAO", "Download concluído (${arq.length() / 1024} KB), abrindo instalador")
                a.runOnUiThread {
                    status("Abrindo instalador…")
                    val uri = FileProvider.getUriForFile(a, "${a.packageName}.arquivos", arq)
                    a.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (e: Exception) {
                Registro.evento("ATUALIZACAO", "Falhou: ${e.message}")
                a.runOnUiThread { status("Falha ao baixar atualização: ${e.message}") }
            }
        }.start()
    }
}
