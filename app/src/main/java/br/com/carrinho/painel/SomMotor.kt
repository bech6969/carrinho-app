package br.com.carrinho.painel

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.Random
import kotlin.math.exp

/**
 * Ronco sintetizado de motor a gasolina 4 cilindros (sem arquivos de audio).
 * [rotacao] 0..1 controla o giro (marcha lenta -> giro alto); [carga] 0..1 deixa o som mais "cheio".
 */
object SomMotor {

    private const val TAXA = 22050
    private const val LENTA_HZ = 28.0     // ~850 rpm de um 4 cilindros (2 explosoes por volta)
    private const val MAX_HZ = 190.0      // ~5700 rpm

    @Volatile var rotacao = 0.0
    @Volatile var carga = 0.0
    @Volatile private var tocando = false
    private var thread: Thread? = null

    fun habilitado(c: Context) = c.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("som_motor", false)
    fun setHabilitado(c: Context, sim: Boolean) =
        c.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("som_motor", sim).apply()

    @Synchronized
    fun iniciar() {
        if (tocando) return
        tocando = true
        thread = Thread { gerar() }.apply { isDaemon = true; start() }
    }

    @Synchronized
    fun parar() {
        tocando = false
        thread?.interrupt()
        thread = null
    }

    @Suppress("DEPRECATION")
    private fun gerar() {
        val minBuf = AudioTrack.getMinBufferSize(TAXA, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack(AudioManager.STREAM_MUSIC, TAXA, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096), AudioTrack.MODE_STREAM)
        } catch (_: Exception) { tocando = false; return }

        val rnd = Random()
        val buf = ShortArray(512)
        val pesoCil = doubleArrayOf(1.0, 0.82, 0.93, 0.78)   // cilindros levemente diferentes = "ronco"
        var fase = 0.0; var hz = LENTA_HZ; var cil = 0; var ampCil = 1.0
        var lp1 = 0.0; var lp2 = 0.0; var dc = 0.0; var vol = 0.0
        try {
            track.play()
            while (tocando) {
                val alvoHz = LENTA_HZ + (MAX_HZ - LENTA_HZ) * rotacao.coerceIn(0.0, 1.0)
                val c = carga.coerceIn(0.0, 1.0)
                val volAlvo = 0.35 + 0.35 * c + 0.2 * rotacao.coerceIn(0.0, 1.0)
                val corte = 0.06 + 0.22 * c + 0.12 * rotacao.coerceIn(0.0, 1.0)
                for (i in buf.indices) {
                    hz += (alvoHz - hz) * 0.0004          // sobe/desce o giro suavemente (~0,1 s)
                    vol += (volAlvo - vol) * 0.0005
                    fase += hz / TAXA
                    if (fase >= 1.0) {
                        fase -= 1.0
                        cil = (cil + 1) % 4
                        ampCil = pesoCil[cil] * (0.9 + 0.2 * rnd.nextDouble())
                    }
                    val explosao = exp(-fase * 7.0) * ampCil
                    val ruido = (rnd.nextDouble() * 2 - 1) * 0.3 * exp(-fase * 4.0)
                    val x = explosao + ruido
                    lp1 += (x - lp1) * corte                // filtro passa-baixa (escapamento)
                    lp2 += (lp1 - lp2) * corte
                    dc += (lp2 - dc) * 0.002                // tira o nivel DC
                    val s = (lp2 - dc) * vol * 2.2
                    buf[i] = (s.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } catch (_: Exception) {
        } finally {
            try { track.stop() } catch (_: Exception) { }
            track.release()
        }
    }
}
