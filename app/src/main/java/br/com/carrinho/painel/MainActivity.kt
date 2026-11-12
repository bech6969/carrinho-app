package br.com.carrinho.painel

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.math.abs

class MainActivity : Activity() {

    enum class Marcha { NEUTRO, FRENTE, RE }

    companion object {
        private const val ACAO_PERMISSAO = "br.com.carrinho.painel.USB_PERMISSAO"
    }

    // ---- Estado compartilhado com a thread de comunicacao ----
    @Volatile private var marcha = Marcha.NEUTRO
    @Volatile private var modo = 1
    @Volatile private var modoPendente = -1
    @Volatile private var pedirReset = false
    @Volatile private var rodando = false
    @Volatile private var rpm = 0
    @Volatile private var kmh = 0.0
    @Volatile private var avisoBackup: String? = null

    private var port: UsbSerialPort? = null
    private var worker: Thread? = null
    private var vbatFiltrada = 0.0

    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var vel: TextView
    private lateinit var unid: TextView
    private lateinit var batPct: TextView
    private lateinit var batBar: ProgressBar
    private lateinit var vbatTxt: TextView
    private lateinit var corrTxt: TextView
    private lateinit var tempTxt: TextView
    private lateinit var bF: Button
    private lateinit var bN: Button
    private lateinit var bR: Button
    private lateinit var bModos: Array<Button>

    private val cinza = Color.parseColor("#2A2F36")

    // ---- USB: permissao concedida / adaptador removido ----
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACAO_PERMISSAO -> conectar()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Registro.evento("USB", "Adaptador USB desconectado")
                    desconectar()
                    mostraStatus("ADAPTADOR USB DESCONECTADO", "#C5221F")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        usb = getSystemService(Context.USB_SERVICE) as UsbManager
        Registro.iniciar(this)
        Registro.evento("APP", "Aplicativo iniciado")

        status = findViewById(R.id.status)
        vel = findViewById(R.id.vel)
        unid = findViewById(R.id.unid)
        batPct = findViewById(R.id.batPct)
        batBar = findViewById(R.id.batBar)
        vbatTxt = findViewById(R.id.vbat)
        corrTxt = findViewById(R.id.corr)
        tempTxt = findViewById(R.id.temp)
        bF = findViewById(R.id.bF)
        bN = findViewById(R.id.bN)
        bR = findViewById(R.id.bR)
        bModos = arrayOf(findViewById(R.id.m0), findViewById(R.id.m1), findViewById(R.id.m2))
        Config.NOME_MODO.forEachIndexed { i, nome -> bModos[i].text = nome }

        bF.setOnClickListener { trocaMarcha(Marcha.FRENTE) }
        bN.setOnClickListener { trocaMarcha(Marcha.NEUTRO) }
        bR.setOnClickListener { trocaMarcha(Marcha.RE) }
        bModos.forEachIndexed { i, b -> b.setOnClickListener { if (i != modo) modoPendente = i } }
        findViewById<Button>(R.id.log).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, RegistrosActivity::class.java)) }
            pinta(this, cinza)
        }

        val parar = findViewById<Button>(R.id.parar)
        parar.setOnClickListener { Registro.evento("PARAR", "Botão PARAR pressionado"); trocaMarcha(Marcha.NEUTRO) }
        pinta(parar, Color.parseColor("#C5221F"))
        val config = findViewById<Button>(R.id.config)
        config.setOnClickListener { startActivity(Intent(this, ConfiguracoesActivity::class.java)) }
        pinta(config, cinza)
        val reset = findViewById<Button>(R.id.reset)
        reset.setOnClickListener { pedirReset = true; Registro.evento("RESET", "Reset de falha solicitado") }
        pinta(reset, cinza)

        unid.text = if (Config.MOSTRAR_KMH) "km/h" else "rpm"
        atualizaBotoes()

        val filtro = IntentFilter().apply {
            addAction(ACAO_PERMISSAO)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filtro, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(usbReceiver, filtro)

        conectar()
        // procura versao nova ao abrir (silencioso se nao houver / sem internet)
        Atualizador.verificar(this, silencioso = true) { avisa(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) conectar()
    }

    override fun onResume() {
        super.onResume()
        telaCheia()
    }

    /** App saiu da tela -> neutro por seguranca (a comunicacao continua para manter leituras). */
    override fun onPause() {
        super.onPause()
        if (marcha != Marcha.NEUTRO) Registro.evento("APP", "App saiu da tela - neutro automático")
        marcha = Marcha.NEUTRO
        atualizaBotoes()
    }

    override fun onDestroy() {
        desconectar()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

    // ---------------- USB ----------------

    private fun conectar() {
        if (rodando) return
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        if (drivers.isEmpty()) {
            mostraStatus("Conecte o adaptador USB-RS485", "#1B1F24")
            return
        }
        val driver = drivers[0]
        if (!usb.hasPermission(driver.device)) {
            val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                this, 0, Intent(ACAO_PERMISSAO).setPackage(packageName), flags
            )
            usb.requestPermission(driver.device, pi)
            mostraStatus("Permita o acesso ao adaptador USB", "#1B1F24")
            return
        }
        try {
            val conn = usb.openDevice(driver.device) ?: run {
                mostraStatus("Falha ao abrir o adaptador USB", "#C5221F"); return
            }
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(Config.BAUD, 8, UsbSerialPort.STOPBITS_1, Config.PARIDADE)
            port = p
            val mb = ModbusRtu(p, Config.SLAVE_ID)
            Conexao.mb = mb
            Registro.evento("USB", "Adaptador conectado (${driver.javaClass.simpleName})")
            iniciaComunicacao(mb)
            if (!Backup.existe(this)) backupInicial(mb)
        } catch (e: Exception) {
            mostraStatus("Erro USB: ${e.message}", "#C5221F")
            desconectar()
        }
    }

    /** Primeira conexao: le e guarda todos os parametros antes de qualquer alteracao. */
    private fun backupInicial(mb: ModbusRtu) {
        Thread {
            try {
                val (f, falhas) = Backup.fazer(this, mb, "Backup inicial automático") { i, n ->
                    if (i % 10 == 0) avisoBackup = "Fazendo backup inicial… $i/$n"
                }
                avisoBackup = null
                avisa("Backup inicial salvo: ${f.name}" + if (falhas > 0) " ($falhas sem resposta)" else "")
            } catch (e: Exception) {
                avisoBackup = null
                Registro.evento("ERRO", "Backup inicial falhou: ${e.message}")
            }
        }.start()
    }

    private fun desconectar() {
        rodando = false
        Conexao.mb = null
        marcha = Marcha.NEUTRO
        worker?.interrupt()
        worker = null
        try { port?.close() } catch (_: Exception) { }
        port = null
        runOnUiThread { atualizaBotoes() }
    }

    // ---------------- Comunicacao com o inversor ----------------

    private fun iniciaComunicacao(mb: ModbusRtu) {
        rodando = true
        worker = Thread {
            var falhas = 0
            var ciclo = 0
            var comPerdida = false
            var ultFalha = 0; var ultAlarme = 0; var ultEstado = -1
            var acelerador = 0.0; var alarme = 0; var falhaAtual = 0
            var marchaAnterior = marcha
            while (rodando) {
                ciclo++
                val inicio = System.currentTimeMillis()
                try {
                    // P0002..P0009 em um bloco: rpm, corrente, Vbat, freq, estado, Vout, km/h, torque
                    val base = mb.readHolding(Config.R_RPM, 8)
                    val comOK = base != null
                    var corrente = 0.0; var vbat = 0.0; var temp = 0.0; var estado = 0
                    var torque = 0.0; var estInv = 0

                    if (base != null) {
                        rpm = base[0].toShort().toInt()
                        corrente = base[1].toShort() / 10.0
                        vbat = base[2] / 10.0
                        kmh = base[Config.R_KMH - Config.R_RPM] / 10.0
                        estInv = base[4]
                        torque = base[7].toShort() / 10.0
                        vbatFiltrada = if (vbatFiltrada == 0.0) vbat else vbatFiltrada * 0.98 + vbat * 0.02
                        mb.readHolding(Config.R_TEMP, 1)?.let { temp = it[0].toShort() / 10.0 }
                        mb.readHolding(Config.R_ESTADO, 1)?.let { estado = it[0] }
                        // a cada 1 s: acelerador, alarme e falha atuais (para o log)
                        if (ciclo % 10 == 0) {
                            mb.readHolding(18, 1)?.let { acelerador = it[0].toShort() / 100.0 }
                            mb.readHolding(48, 2)?.let { alarme = it[0]; falhaAtual = it[1] }
                        }
                    }
                    Conexao.rpm = if (comOK) rpm else 0
                    Conexao.girando = comOK && (estado and (1 shl 8)) != 0

                    falhas = if (comOK) 0 else falhas + 1
                    if (falhas == Config.FALHAS_P_NEUTRO) {
                        comPerdida = true
                        Registro.evento("COMUNICACAO", "Sem resposta do inversor" +
                            if (marcha != Marcha.NEUTRO) " - neutro automático" else "")
                    }
                    if (falhas >= Config.FALHAS_P_NEUTRO && marcha != Marcha.NEUTRO) marcha = Marcha.NEUTRO
                    if (comOK && comPerdida) { comPerdida = false; Registro.evento("COMUNICACAO", "Comunicação restabelecida") }

                    // ---- eventos do inversor ----
                    if (comOK) {
                        if (falhaAtual != ultFalha) {
                            if (falhaAtual != 0) Registro.evento("FALHA", Falhas.descreve("F", falhaAtual))
                            else Registro.evento("FALHA", "Falha ${Falhas.descreve("F", ultFalha)} resetada")
                            ultFalha = falhaAtual
                        }
                        if (alarme != ultAlarme) {
                            if (alarme != 0) Registro.evento("ALARME", Falhas.descreve("A", alarme))
                            else Registro.evento("ALARME", "Alarme ${Falhas.descreve("A", ultAlarme)} terminou")
                            ultAlarme = alarme
                        }
                        if (estInv != ultEstado) {
                            val nomes = arrayOf("Pronto", "Execução", "Subtensão", "Falha", "Autoajuste", "Configuração", "Frenagem CC", "Sem Função")
                            Registro.evento("ESTADO", "Inversor: ${nomes.getOrElse(estInv) { "$estInv" }}")
                            ultEstado = estInv
                        }
                    }
                    if (marcha != marchaAnterior) {
                        Registro.evento("MARCHA", "${marchaAnterior.name} → ${marcha.name} (${Math.round(abs(kmh))} km/h)")
                        marchaAnterior = marcha
                    }

                    // ---- telemetria: 1 s andando, 10 s parado ----
                    val andando = abs(rpm) > 0
                    if (comOK && (if (andando) ciclo % 10 == 0 else ciclo % 100 == 0)) {
                        Registro.dados(marcha.name, Config.NOME_MODO[modo], kmh, rpm, corrente, vbat,
                            temp, torque, acelerador, estInv, estado, alarme, falhaAtual)
                    }

                    // Palavra de controle (escrita periodica alimenta o watchdog P0314)
                    var cmd = when (marcha) {
                        Marcha.FRENTE -> Config.CMD_FRENTE
                        Marcha.RE -> Config.CMD_RE
                        Marcha.NEUTRO -> Config.CMD_NEUTRO
                    }
                    if (pedirReset) { cmd = Config.CMD_RESET; pedirReset = false }
                    mb.writeSingle(Config.R_CONTROLE, cmd)

                    // Modo de conducao: grava so quando muda
                    val mp = modoPendente
                    if (mp >= 0) {
                        if (mb.writeSingle(Config.R_VELMAX, Config.VEL_MAX_MODO[mp])) {
                            Registro.evento("MODO", "${Config.NOME_MODO[modo]} → ${Config.NOME_MODO[mp]} (P0134 = ${Config.VEL_MAX_MODO[mp]} rpm)")
                            modo = mp
                        } else { avisa("Falha ao trocar modo"); Registro.evento("ERRO", "Falha ao trocar modo para ${Config.NOME_MODO[mp]}") }
                        modoPendente = -1
                    }

                    val r = rpm; val v = vbat; val c = corrente; val t = temp; val e = estado
                    runOnUiThread { atualizaTela(comOK, r, v, c, t, e) }
                } catch (ex: Exception) {
                    Registro.evento("ERRO", "Erro de comunicação USB: ${ex.message}")
                    runOnUiThread { mostraStatus("Erro de comunicação: ${ex.message}", "#C5221F") }
                    desconectar()
                    break
                }
                val espera = Config.CICLO_MS - (System.currentTimeMillis() - inicio)
                if (espera > 0) try { Thread.sleep(espera) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    // ---------------- Interface ----------------

    private fun trocaMarcha(nova: Marcha) {
        if (!rodando) { avisa("Sem conexão com o inversor"); return }
        if (nova == Marcha.NEUTRO || nova == marcha || abs(rpm) <= Config.RPM_TROCA_MAX) {
            marcha = nova
            atualizaBotoes()
        } else {
            avisa("Pare o carrinho antes de trocar F/R")
            Registro.evento("BLOQUEIO", "Troca para ${nova.name} recusada em movimento (${Math.round(abs(kmh))} km/h)")
        }
    }

    private fun atualizaTela(comOK: Boolean, rpm: Int, vbat: Double, corrente: Double, temp: Double, estado: Int) {
        vel.text = if (Config.MOSTRAR_KMH) Math.round(abs(kmh)).toString() else abs(rpm).toString()

        val pct = (((vbatFiltrada - Config.V_BAT_VAZIA) / (Config.V_BAT_CHEIA - Config.V_BAT_VAZIA)) * 100)
            .toInt().coerceIn(0, 100)
        batPct.text = "$pct%"
        batBar.progress = pct
        batBar.progressTintList = ColorStateList.valueOf(
            Color.parseColor(if (pct < 20) "#D93025" else if (pct < 40) "#F9AB00" else "#34C759")
        )
        vbatTxt.text = String.format("%.1f V", vbat)
        corrTxt.text = String.format("%.0f A", corrente)
        tempTxt.text = String.format("%.0f °C", temp)
        tempTxt.setTextColor(Color.parseColor(if (temp > 80) "#FF5252" else "#E8EAED"))

        val ab = avisoBackup
        when {
            ab != null -> mostraStatus(ab, "#1A73E8")
            !comOK -> mostraStatus("SEM COMUNICAÇÃO COM O INVERSOR", "#C5221F")
            estado and (1 shl 15) != 0 -> mostraStatus("FALHA", "#C5221F")
            estado and (1 shl 7) != 0 -> mostraStatus("ALARME", "#F9AB00", escuro = true)
            estado and (1 shl 13) != 0 -> mostraStatus("SUBTENSÃO", "#F9AB00", escuro = true)
            estado and (1 shl 8) != 0 -> mostraStatus("GIRANDO", "#1E8E3E")
            else -> mostraStatus("PRONTO", "#1B1F24")
        }
        atualizaBotoes()
    }

    private fun atualizaBotoes() {
        pinta(bF, if (marcha == Marcha.FRENTE) Color.parseColor("#1E8E3E") else cinza)
        pinta(bN, if (marcha == Marcha.NEUTRO) Color.parseColor("#1A73E8") else cinza)
        pinta(bR, if (marcha == Marcha.RE) Color.parseColor("#E8710A") else cinza)
        bModos.forEachIndexed { i, b -> pinta(b, if (i == modo) Color.parseColor("#8430CE") else cinza) }
    }

    private fun pinta(b: Button, cor: Int) {
        b.background = GradientDrawable().apply {
            cornerRadius = 16 * resources.displayMetrics.density
            setColor(cor)
        }
        b.setTextColor(Color.WHITE)
    }

    private fun mostraStatus(txt: String, cor: String, escuro: Boolean = false) {
        status.text = txt
        status.setBackgroundColor(Color.parseColor(cor))
        status.setTextColor(if (escuro) Color.BLACK else Color.WHITE)
    }

    private fun avisa(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun telaCheia() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
}
