package br.com.carrinho.painel

import kotlin.math.abs

/**
 * Estimativa de carga (%) da bateria que nao "despenca" nas subidas.
 *
 * 1. Compensacao da queda de tensao: sob carga a tensao cai por causa da resistencia interna
 *    (V = Vrepouso - I*R). Estimamos Vrepouso = V + I*R. O R e aprendido sozinho, observando
 *    quanto a tensao muda quando a corrente muda de repente (dV/dI).
 * 2. Filtro lento: a tensao compensada passa por uma media movel exponencial
 *    (~20 s parado, ~3 min andando, quando a medida e menos confiavel).
 * 3. Tabela tensao de repouso -> % (chumbo-acido 48 V, 24 celulas), que nao e linear.
 * 4. O numero mostrado so desce 1% por vez e so sobe com o carrinho parado ha 1 min
 *    (evita subir depois de soltar o acelerador ou na frenagem regenerativa).
 *
 * Obs.: o CVW300 nao informa a corrente da bateria, so a do motor (P0003). Ela e
 * proporcional o suficiente para a compensacao, ja que o R aprendido absorve a escala.
 */
class Bateria {

    private var rEst = Config.R_BAT_INICIAL
    private var vFilt = 0.0
    private var vAnt = 0.0; private var iAnt = 0.0
    private var mostrado = -1
    private var tempoParado = 0.0
    private var tempoDesdeMudanca = 0.0

    /** Chamar a cada leitura. [dt] em segundos. Retorna a % a mostrar. */
    fun atualizar(v: Double, iMotor: Double, dt: Double): Int {
        val i = abs(iMotor)
        if (v < 10) return mostrado.coerceAtLeast(0)

        // aprende R em degraus de corrente (acelerou ou soltou de repente)
        if (vAnt > 0) {
            val di = i - iAnt
            if (abs(di) > 15) {
                val r = -(v - vAnt) / di
                if (r in 0.001..0.2) rEst += (r - rEst) * 0.05
            }
        }
        vAnt = v; iAnt = i

        val vRepouso = v + i * rEst
        val parado = i < 3
        tempoParado = if (parado) tempoParado + dt else 0.0
        val tau = if (parado) 20.0 else 180.0
        vFilt = if (vFilt == 0.0) vRepouso else vFilt + (vRepouso - vFilt) * (dt / tau).coerceAtMost(1.0)

        val alvo = porcentagem(vFilt)
        tempoDesdeMudanca += dt
        if (mostrado < 0) { mostrado = alvo; tempoDesdeMudanca = 0.0 }
        else if (alvo < mostrado && tempoDesdeMudanca > 10) { mostrado--; tempoDesdeMudanca = 0.0 }
        else if (alvo > mostrado && tempoParado > 60 && tempoDesdeMudanca > 5) { mostrado++; tempoDesdeMudanca = 0.0 }
        return mostrado
    }

    val resistencia get() = rEst

    /** Tensao de repouso -> % pela tabela (interpolacao linear entre pontos). */
    private fun porcentagem(v: Double): Int {
        val t = Config.TABELA_BAT
        if (v <= t.first().first) return 0
        if (v >= t.last().first) return 100
        for (k in 1 until t.size) {
            val (v1, p1) = t[k]; val (v0, p0) = t[k - 1]
            if (v <= v1) return (p0 + (p1 - p0) * (v - v0) / (v1 - v0)).toInt()
        }
        return 100
    }
}
