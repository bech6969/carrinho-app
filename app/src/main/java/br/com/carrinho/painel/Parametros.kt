package br.com.carrinho.painel

/**
 * Parametros configuraveis do WEG CVW300 (G2), conforme a tabela de referencia
 * rapida do manual do usuario CVW300 (doc. 10001832424 rev. 07, software V2.0X). Parametros somente-leitura
 * e as palavras de controle usadas pelo app (P0682/P0683) ficam de fora.
 *
 * Convencao WEG: valor no registrador = valor exibido x 10^dec.
 * Faixas "x Inom" foram convertidas considerando inversor de 200 A (o inversor recusa valores fora do modelo).
 */
data class Param(
    val num: Int,
    val nome: String,
    val grupo: String,
    val min: Int = 0,               // em unidades brutas (ja multiplicado por 10^dec)
    val max: Int = 65535,
    val dec: Int = 0,
    val unidade: String = "",
    val padrao: String = "",
    val opcoes: List<Pair<Int, String>>? = null,
    val alerta: String? = null
) {
    val assinado get() = min < 0
    val codigo get() = "P" + num.toString().padStart(4, '0')
}

object Parametros {

    private val lista = mutableListOf<Param>()
    private var grupo = ""

    private fun g(nome: String) { grupo = nome }

    /** Numerico. min/max no valor exibido; convertidos com [dec]. */
    private fun n(num: Int, nome: String, min: Double, max: Double, dec: Int = 0,
                  un: String = "", padrao: String = "", alerta: String? = null) {
        var f = 1.0; repeat(dec) { f *= 10 }
        lista += Param(num, nome, grupo, Math.round(min * f).toInt(), Math.round(max * f).toInt(),
            dec, un, padrao, null, alerta)
    }

    /** Lista de opcoes no formato "0=Texto;1=Texto". */
    private fun o(num: Int, nome: String, opcoes: String, padrao: String = "", alerta: String? = null) {
        val ops = opcoes.split(";").map {
            val (k, v) = it.split("=", limit = 2); k.trim().toInt() to v.trim()
        }
        lista += Param(num, nome, grupo, ops.minOf { it.first }, ops.maxOf { it.first },
            0, "", padrao, ops, alerta)
    }

    private const val PARADO = "Alterar somente com o carrinho parado."
    private const val COMUNIC = "Muda a comunicação: depois de salvar, ajuste o app (Config.kt) para o novo valor, senão o app perde a conexão."
    private const val ESPECIALISTA = "Ajuste de especialista: valor errado pode deixar o motor instável."

    private val DI = "0=Sem Função;1=Gira/Para;2=Habilita Geral;3=Parada Rápida;4=Sentido Giro;5=LOC/REM;6=JOG;7=SoftPLC;8=JOG+;9=JOG-;10=Sem Alarme Ext.;11=Sem Falha Ext.;12=Reset;13=Desab. FlyStart;14=Regul. Bateria;15=Bloqueia Prog.;16=Carrega Us. 1;17=Carrega Us. 2"
    private val DO = "0=Sem Função;1=N* > Nx;2=N > Nx;3=N < Ny;4=N = N*;5=Veloc. Nula;6=Is > Ix;7=Is < Ix;8=Torque > Tx;9=Torque < Tx;10=Remoto;11=Run;12=Ready;13=Sem Falha;14=Sem Função;15=Sem F071;16=Sem F021/F022;17=Sem F051;18=Sem F072;19=Conteúdo P0695;20=Sent. Horário;21=Pré-Carga OK;22=Com Falha;23=SoftPLC;24=N>Nx/Nt>Nx;25=F > Fx (1);26=F > Fx (2);27=Sem Alarme;28=Sem Falha/Alarme"
    private val AO = "0=Ref. Veloc.;1=Ref. Total;2=Veloc. Real;3=Ref. Cor. Torque;4=Corr. Torque;5=Corrente Saída;6=Corrente Ativa;7=Potência Saída;8=Corr. Torque>0;9=Torque Motor;10=SoftPLC;11=Ixt Motor;12=Veloc. Encoder;13=Conteúdo P0696;14=Conteúdo P0697;15=Corrente Id*"
    private val AI_FUNC = "0=Ref. Veloc.;1=N* sem Rampa;2=Máx. Cor. Torque;3=SoftPLC"
    private val REF = "0=HMI;1=Acelerador (AI1);2=AI2;3=Soma AIs > 0;4=Soma AIs;5=Serial/USB;6=CAN;7=SoftPLC"
    private val GIRO = "0=Horário;1=Anti-Horário;2=Tecla SG (H);3=Tecla SG (AH);4=DIx;5=Serial/USB (H);6=Serial/USB (AH);7=CAN (H);8=CAN (AH);9=SoftPLC (H);10=SoftPLC (AH);11=Polaridade AI2"
    private val GIRAPARA = "0=Teclas I,O;1=DIx;2=Serial/USB;3=CAN;4=SoftPLC"
    private val JOG = "0=Inativo;1=Tecla JOG;2=DIx;3=Serial/USB;4=CAN;5=SoftPLC"
    private val INATIVO_ATIVO = "0=Inativo;1=Ativo"
    private val FALHA_ALARME = "0=Inativa;1=Falha/Alarme;2=Falha;3=Alarme"
    private val FORMA = "0=wxyz;1=wxy.z;2=wx.yz;3=w.xyz"
    private val UNIDADE = "0=Nenhuma;1=V;2=A;3=rpm;4=s;5=ms;6=N;7=m;8=Nm;9=mA;10=%;11=°C;12=CV;13=Hz;14=HP;15=h;16=W;17=kW;18=kWh;19=H"

    val todos: List<Param> by lazy {
        g("Acesso e senha")
        n(0, "Acesso aos Parâmetros", 0.0, 9999.0, padrao = "0")
        o(200, "Senha", "0=Inativa;1=Ativa;2=Alterar Senha", "1", "Se ativar sem saber a senha, você perde acesso às alterações.")

        g("Rampas e velocidade")
        n(100, "Tempo de Aceleração", 0.0, 999.0, 1, "s", "20,0")
        n(101, "Tempo de Desaceleração", 0.0, 999.0, 1, "s", "20,0")
        o(104, "Rampa", "0=Linear;1=Curva S", "0")
        n(121, "Rotação de Referência", 0.0, 18000.0, 0, "rpm", "90")
        n(122, "Referência JOG/JOG+", 0.0, 18000.0, 0, "rpm", "150")
        n(123, "Referência JOG-", 0.0, 18000.0, 0, "rpm", "150")
        n(133, "Rotação Mínima", 0.0, 18000.0, 0, "rpm", "0")
        n(134, "Rotação Máxima", 0.0, 18000.0, 0, "rpm", "6000", "Os modos ECO/NORMAL/SPORT do painel também gravam este parâmetro.")
        n(132, "Nível Máx. Sobrevelocidade", 0.0, 100.0, 0, "%", "10")
        o(126, "Habilita Trigger de Velocidade", INATIVO_ATIVO, "0")
        n(127, "Velocidade Trigger", 5.0, 200.0, 1, "km/h", "5,0")

        g("Veículo e bateria")
        n(124, "Diâmetro da Roda", 100.0, 1000.0, 0, "mm", "562")
        n(125, "Relação de Transmissão", 1.0, 50.0, 2, "", "8,00")
        o(296, "Tensão Nominal da Bateria", "0=24 V;1=36 V;2=48 V;3=60 V;4=72 V", "4", PARADO)
        n(603, "Ganho Bateria Sub/F021", 0.6, 1.2, 3, "", "1,000")

        g("Corrente e torque")
        n(135, "Corrente Máxima Saída", 0.2, 400.0, 1, "A", "1,5×Inom", "Faixa real depende do modelo (até 2×Inom).")
        n(169, "Máxima Corrente de Torque (+)", 0.0, 400.0, 1, "%", "125,0")
        n(170, "Máxima Corrente de Torque (-)", 0.0, 400.0, 1, "%", "125,0")
        o(344, "Conf. Lim. Corrente", "0=Hold - LR ON;1=Desac. - LR ON;2=Hold - LR OFF;3=Desac. - LR OFF", "1")

        g("Tipo de controle")
        o(202, "Tipo de Controle", "0=V/f;1=Sensorless;2=Encoder", "2", "$PARADO Depois de trocar, refaça o autoajuste.")

        g("Controle V/f")
        n(136, "Boost de Torque Manual", 0.0, 9.0, 0, "", "1")
        n(137, "Boost de Torque Automático", 0.0, 1.0, 2, "", "0,00")
        n(138, "Compensação de Escorregamento", -10.0, 10.0, 1, "%", "0,0")
        n(142, "Tensão de Saída Máxima", 0.0, 100.0, 1, "%", "100,0")
        n(143, "Tensão de Saída Intermediária", 0.0, 100.0, 1, "%", "50,0")
        n(144, "Tensão de Saída em 3 Hz", 0.0, 100.0, 1, "%", "4,0")
        n(145, "Velocidade Início Enfraquecimento de Campo", 0.0, 18000.0, 0, "rpm", "2340")
        n(146, "Velocidade Intermediária", 0.0, 18000.0, 0, "rpm", "900")
        o(150, "Tipo de Regulação de Bateria V/f", "0=Hold Rampa;1=Acelera Rampa", "0")
        n(151, "Nível de Regulação de Bateria V/f", 27.0, 99.0, 0, "V", "99")
        n(152, "Ganho Proporcional Regulação de Bateria", 0.0, 9.99, 2, "", "1,50")
        n(190, "Tensão de Saída Máxima", 0.0, 72.0, 0, "V", "72")

        g("Controle vetorial")
        n(161, "Ganho Proporcional de Velocidade", 0.0, 63.9, 1, "", "7,4", ESPECIALISTA)
        n(162, "Ganho Integral de Velocidade", 0.0, 9.999, 3, "", "0,023", ESPECIALISTA)
        n(163, "Offset Referência LOC", -999.0, 999.0, 0, "", "0")
        n(164, "Offset Referência REM", -999.0, 999.0, 0, "", "0")
        n(165, "Filtro de Velocidade", 0.012, 1.0, 3, "s", "0,012")
        n(166, "Ganho Diferencial de Velocidade", 0.0, 7.99, 2, "", "0,00", ESPECIALISTA)
        n(167, "Ganho Proporcional de Corrente", 0.0, 1.99, 2, "", "0,50", ESPECIALISTA)
        n(168, "Ganho Integral de Corrente", 0.0, 1.999, 3, "", "0,010", ESPECIALISTA)
        n(175, "Ganho Proporcional de Fluxo", 0.0, 31.9, 1, "", "2,0", ESPECIALISTA)
        n(176, "Ganho Integral de Fluxo", 0.0, 9.999, 3, "", "0,020", ESPECIALISTA)
        n(178, "Fluxo Nominal", 0.0, 120.0, 0, "%", "100")
        n(179, "Fluxo Máximo", 0.0, 120.0, 0, "%", "120")
        n(180, "Iq* após o I/f", 0.0, 350.0, 0, "%", "30")
        n(182, "Rotação para Atuação I/f", 0.0, 90.0, 0, "rpm", "18")
        n(183, "Corrente no Modo I/f", 0.0, 9.0, 0, "", "1")
        o(184, "Modo Regulação de Bateria", "0=Com Perdas;1=Sem Perdas;2=Hab./Desab. DIx", "1")
        n(185, "Nível de Regulação de Bateria", 27.0, 99.0, 0, "V", "99")
        n(186, "Ganho Proporcional de Bateria", 0.0, 63.9, 1, "", "40,0", ESPECIALISTA)
        n(187, "Ganho Integral de Bateria", 0.0, 9.999, 3, "", "0,090", ESPECIALISTA)
        n(414, "Tempo de Magnetização", 0.0, 9.999, 3, "s", "0,100")

        g("Proteções")
        n(139, "Filtro Corrente de Saída", 0.0, 16.0, 1, "s", "0,2")
        n(156, "Corrente de Sobrecarga 100%", 0.1, 300.0, 1, "A", "1,05×Inom")
        n(157, "Corrente de Sobrecarga 50%", 0.1, 300.0, 1, "A", "0,9×Inom")
        n(158, "Corrente de Sobrecarga 5%", 0.1, 300.0, 1, "A", "0,65×Inom")
        o(159, "Classe Térmica do Motor", "0=Classe 5;1=Classe 10;2=Classe 15;3=Classe 20;4=Classe 25;5=Classe 30;6=Classe 35;7=Classe 40;8=Classe 45", "1")
        o(341, "Configuração AIPTC", FALHA_ALARME, "0")
        o(348, "Conf. Sobrecarga Motor", FALHA_ALARME, "0")
        n(349, "Nível para Alarme Ixt", 70.0, 100.0, 0, "%", "85")
        o(350, "Conf. Sobrecarga Mosfets", "2=Falha;3=Falha/Alarme", "3")
        o(353, "Conf. Sobretemp. MOSFET/Ar", "0=D-F/A, AR-F/A;1=D-F/A, AR-F;2=D-F, AR-F/A;3=D-F, AR-F", "0")
        o(358, "Config. Falha Encoder", "0=Inativas;1=F067 Ativa;2=F079 Ativa;3=F067, F079 Ativas", "3")
        n(398, "Fator Serviço Motor", 1.0, 1.5, 2, "", "1,00")
        n(340, "Tempo Auto-Reset", 0.0, 200.0, 0, "s", "0")

        g("Comandos (LOC/REM)")
        o(220, "Seleção Fonte LOC/REM", "0=Sempre LOC;1=Sempre REM;2=Tecla LR (LOC);3=Tecla LR (REM);4=DIx;5=Serial/USB LOC;6=Serial/USB REM;7=CAN LOC;8=CAN REM;9=SoftPLC LOC;10=SoftPLC REM", "2", PARADO)
        o(221, "Sel. Referência LOC", REF, "0", PARADO)
        o(222, "Sel. Referência REM", REF, "1", PARADO)
        o(223, "Seleção Giro LOC", GIRO, "2", "$PARADO O app usa 5 = Serial/USB (H) para F/N/R.")
        o(224, "Seleção Gira/Para LOC", GIRAPARA, "0", "$PARADO O app usa 2 = Serial/USB.")
        o(225, "Seleção JOG LOC", JOG, "1")
        o(226, "Seleção Giro REM", GIRO, "4", PARADO)
        o(227, "Seleção Gira/Para REM", GIRAPARA, "1", PARADO)
        o(228, "Seleção JOG REM", JOG, "2")
        o(229, "Seleção Modo Parada", "0=Por Rampa;1=Por Inércia;2=Parada Rápida;3=Por Rampa Iq* = 0;4=ParRápida Iq* = 0", "0")
        o(217, "Bloqueio por Rot. Nula", INATIVO_ATIVO, "0")
        o(218, "Saída Bloq. Rot. Nula", "0=Ref. ou Rot.;1=Referência", "0")
        n(219, "Tempo com Rot. Nula", 0.0, 999.0, 0, "s", "0")

        g("Acelerador (AI1) e AI2")
        o(131, "Inicia Calibração", "0=Inativa;1=AI1;2=AI2", "0", "Inicia a calibração do pedal — siga o procedimento do manual.")
        o(230, "Zona Morta (AIs)", "0=Inativa;1=Ativa", "0")
        o(231, "Função do Acelerador (AI1)", AI_FUNC, "0")
        n(232, "Ganho do Acelerador (AI1)", 0.0, 9.999, 3, "", "1,000")
        o(233, "Tipo de Acelerador (AI1)", "0=0 a 10 V;1=-10 a +10 V", "0")
        n(234, "Offset Acelerador (AI1)", -100.0, 100.0, 2, "%", "0,00")
        n(235, "Filtro do Acelerador (AI1)", 0.0, 16.0, 2, "s", "0,00")
        n(242, "Valor Máximo do Acelerador (AI1)", 0.0, 100.0, 1, "%", "100,0")
        n(243, "Valor Mínimo do Acelerador (AI1)", -100.0, 0.0, 1, "%", "-100,0")
        n(244, "Percentual de Zona Morta", 0.0, 100.0, 0, "%", "5")
        n(245, "Valor do Offset de Calibração", -100.0, 100.0, 1, "%", "0,0")
        o(236, "Função do Sinal AI2", AI_FUNC, "0")
        n(237, "Ganho da Entrada AI2", 0.0, 9.999, 3, "", "1,000")
        o(238, "Sinal da Entrada AI2", "0=0 a 10 V;1=-10 a +10 V", "0")
        n(239, "Offset da Entrada AI2", -100.0, 100.0, 2, "%", "0,00")
        n(240, "Filtro da Entrada AI2", 0.0, 16.0, 2, "s", "0,00")
        n(246, "Valor Máximo da AI2", 0.0, 100.0, 1, "%", "100,0")
        n(247, "Valor Mínimo da AI2", -100.0, 0.0, 1, "%", "-100,0")
        n(248, "Percentual de Zona Morta AI2", 0.0, 100.0, 0, "%", "5")
        n(249, "Valor do Offset de Calibração AI2", -100.0, 100.0, 1, "%", "0,0")

        g("Entradas digitais")
        o(263, "Função da Entrada DI1", DI, "1")
        o(264, "Função da Entrada DI2", DI, "4")
        o(265, "Função da Entrada DI3", DI, "0")
        o(266, "Função da Entrada DI4", DI, "0")
        o(267, "Função da Entrada DI5", DI, "0")
        o(268, "Função da Entrada DI6", DI, "0")
        o(269, "Função da Entrada DI7", DI, "0")
        o(270, "Função da Entrada DI8", DI, "0")

        g("Saídas digitais")
        o(275, "Função Saída DO1 (RL1)", DO, "13")
        o(276, "Função Saída DO2", DO, "2")
        o(277, "Função Saída DO3", DO, "1")
        o(278, "Função Saída DO4", DO, "0")
        o(279, "Função Saída DO5", DO, "0")
        n(281, "Frequência Fx", 0.0, 300.0, 1, "Hz", "4,0")
        n(282, "Histerese Fx", 0.0, 15.0, 1, "Hz", "2,0")
        n(287, "Histerese Nx/Ny", 0.0, 900.0, 0, "rpm", "18")
        n(288, "Velocidade Nx", 0.0, 18000.0, 0, "rpm", "120")
        n(289, "Velocidade Ny", 0.0, 18000.0, 0, "rpm", "1800")
        n(290, "Corrente Ix", 0.0, 400.0, 1, "A", "1,0×Inom")
        n(291, "Rotação Nula", 0.0, 18000.0, 0, "rpm", "18")
        n(292, "Faixa para N = N*", 0.0, 18000.0, 0, "rpm", "18")
        n(293, "Torque Tx", 0.0, 200.0, 0, "%", "100")
        n(695, "Valor para DOx (bits DO1–DO5)", 0.0, 31.0, 0, "", "0")

        g("Saídas analógicas")
        o(251, "Função da Saída AO1", AO, "2")
        n(252, "Ganho da Saída AO1", 0.0, 9.999, 3, "", "1,000")
        o(253, "Sinal da Saída AO1", "0=0 a 10 V;1=10 V a 0", "0")
        o(254, "Função da Saída AO2", AO, "5")
        n(255, "Ganho da Saída AO2", 0.0, 9.999, 3, "", "1,000")
        o(256, "Sinal da Saída AO2", "0=0 a 10 V;1=10 V a 0", "0")
        n(696, "Valor 1 para AOx", -32768.0, 32767.0, 0, "", "0")
        n(697, "Valor 2 para AOx", -32768.0, 32767.0, 0, "", "0")

        g("Frenagem e partida")
        n(299, "Tempo Frenagem CC Partida", 0.0, 15.0, 1, "s", "0,0")
        n(300, "Tempo Frenagem Parada", 0.0, 15.0, 1, "s", "0,0")
        n(301, "Rotação de Início", 0.0, 450.0, 0, "rpm", "30")
        n(302, "Tensão Frenagem CC", 0.0, 10.0, 1, "%", "2,0")
        n(372, "Corr. Fren. CC Sless", 0.0, 90.0, 1, "%", "40,0")
        o(320, "Flying Start", "0=Inativo;1=Flying Start", "0")
        n(327, "Rampa Corr. I/f F.S.", 0.0, 1.0, 3, "s", "0,070")
        n(328, "Filtro Flying Start", 0.0, 1.0, 3, "s", "0,085")
        n(329, "Rampa Freq. I/f F.S.", 2.0, 50.0, 1, "", "6,0")
        n(331, "Rampa de Tensão", 0.2, 60.0, 1, "s", "2,0")
        n(332, "Tempo Morto", 0.1, 10.0, 1, "s", "1,0")
        o(356, "Compens. Tempo Morto", "0=Inativa;1=Ativa", "1")

        g("Dados do motor")
        n(400, "Tensão Nominal Motor", 0.0, 72.0, 0, "V", "51", "$PARADO Não pode passar da tensão da bateria (P0296). Só vale após desligar/ligar o inversor.")
        n(401, "Corrente Nom. Motor", 0.0, 260.0, 1, "A", "1,0×Inom", PARADO)
        n(402, "Rotação Nom. Motor", 0.0, 18000.0, 0, "rpm", "1750", PARADO)
        n(403, "Frequência Nom. Motor", 0.0, 300.0, 0, "Hz", "60", PARADO)
        o(404, "Potência Nom. Motor", "0=3 kW;1=6 kW;2=12 kW", "conforme motor", PARADO)
        n(405, "Número Pulsos Encoder", 32.0, 9999.0, 0, "ppr", "256", PARADO)
        o(406, "Ventilação do Motor", "0=Autoventilado;1=Independente", "1")
        o(408, "Fazer Autoajuste", "0=Não;1=Sem Girar;2=Girar para Im;3=Girar para Tm;4=Estimar Tm", "0",
            "O autoajuste pode GIRAR O MOTOR. Rodas suspensas e siga o manual.")
        n(409, "Resistência Estator", 0.0, 9.999, 3, "ohm", "0,000", ESPECIALISTA)
        n(410, "Corrente Magnetização", 0.0, 250.0, 1, "A", "0", ESPECIALISTA)
        n(411, "Indutância Dispersão", 0.0, 99.99, 2, "mH", "0,00", ESPECIALISTA)
        n(412, "Constante Tr", 0.0, 9.999, 3, "s", "0,000", ESPECIALISTA)
        n(413, "Constante Tm", 0.0, 99.99, 2, "s", "0,00", ESPECIALISTA)
        o(317, "Start-up Orientado", "0=Não;1=Sim", "0", "Entra no modo de configuração guiada do inversor.")

        g("Comunicação serial")
        n(308, "Endereço Serial", 1.0, 247.0, 0, "", "1", COMUNIC)
        o(310, "Taxa Comunic. Serial", "0=9600 bits/s;1=19200 bits/s;2=38400 bits/s;3=57600 bits/s", "1", COMUNIC)
        o(311, "Config. Bytes Serial", "0=8 bits, sem, 1;1=8 bits, par, 1;2=8 bits, ímp, 1;3=8 bits, sem, 2;4=8 bits, par, 2;5=8 bits, ímp, 2", "1", COMUNIC)
        o(313, "Ação p/ Erro Comunic.", "0=Inativo;1=Para por Rampa;2=Desab. Geral;3=Vai para LOC;4=LOC Mantém Hab.;5=Causa Falha", "1",
            "Segurança: mantenha em 1 ou 2 para o carrinho parar se o tablet desconectar.")
        n(314, "Watchdog Serial", 0.0, 999.0, 1, "s", "0,0",
            "Segurança: use ~0,5 s. Com 0,0 o inversor NÃO para se o tablet travar.")

        g("CAN automotivo")
        o(700, "Protocolo CAN Automotivo", "0=Desabilitado;1=CanAutomotivo", "0")
        n(701, "Endereço CAN Automotivo", 0.0, 127.0, 0, "", "63")
        o(702, "Taxa Comunicação CAN", "0=1 Mbps/Auto;1=Reservado/Auto;2=500 Kbps;3=250 Kbps;4=125 Kbps;5=100 Kbps/Auto;6=50 Kbps/Auto;7=20 Kbps/Auto;8=10 Kbps/Auto", "0")
        o(703, "Reset de Bus Off", "0=Manual;1=Automático", "1")

        g("HMI do inversor")
        n(205, "Sel. Parâm. Disp. Principal", 0.0, 1199.0, 0, "", "2")
        n(206, "Sel. Parâm. Disp. Secundário", 0.0, 1199.0, 0, "", "1")
        n(207, "Sel. Parâm. Barra Gráfica", 0.0, 1199.0, 0, "", "3")
        n(208, "Fator Esc. Disp. Principal", 0.1, 1000.0, 1, "%", "100,0")
        o(209, "Unidade Eng. Disp. Principal", UNIDADE, "3")
        o(210, "Forma Ind. Disp. Principal", FORMA, "0")
        n(211, "Fator Esc. Disp. Secundário", 0.1, 1000.0, 1, "%", "100,0")
        o(212, "Forma Ind. Disp. Secundário", FORMA, "0")
        n(213, "Fundo Escala Bar. Gráfica", 1.0, 65535.0, 0, "", "1")
        n(216, "Iluminação Display HMI", 0.0, 15.0, 0, "", "15")

        g("Memória e perfis")
        o(204, "Carrega/Salva Parâm.", "0=Sem Função;5=Carrega Encoder;7=Carr. Usuário 1;8=Carr. Usuário 2;9=Salva Usuário 1;10=Salva Usuário 2", "0",
            "Carregar um perfil substitui TODOS os parâmetros atuais.")
        o(303, "Salva Par. Memória", INATIVO_ATIVO, "0", "O botão Salvar do app já grava P0303 = 1 automaticamente ao final.")

        g("SoftPLC")
        o(1001, "Comando para SoftPLC", "0=Para Aplic.;1=Executa Aplic.;2=Exclui Aplic.", "0")
        for (i in 1..50) n(1009 + i, "Parâmetro SoftPLC $i", -32768.0, 32767.0, 0, "", "0")

        lista.toList()
    }

    /** Parametros somente-leitura (entram no backup como registro, nunca sao gravados). */
    val somenteLeitura: List<Param> by lazy {
        fun r(num: Int, nome: String, dec: Int = 0, un: String = "", assinado: Boolean = false) =
            Param(num, nome, "Leitura", if (assinado) -32768 else 0, if (assinado) 32767 else 65535, dec, un)
        listOf(
            r(1, "Referência de Rotação", 0, "rpm"), r(2, "Rotação do Motor", 0, "rpm"),
            r(3, "Corrente do Motor", 1, "A", true), r(4, "Tensão da Bateria", 1, "V"),
            r(5, "Frequência do Motor", 1, "Hz"), r(6, "Estado do Inversor"),
            r(7, "Tensão de Saída", 1, "V"), r(8, "Velocidade do Veículo", 1, "km/h"),
            r(9, "Torque no Motor", 1, "%", true), r(12, "Estado DI8 a DI1"), r(13, "Estado DO5 a DO1"),
            r(14, "Valor de AO1", 2, "%"), r(15, "Valor de AO2", 2, "%"),
            r(18, "Valor do Acelerador", 2, "%", true), r(19, "Valor de AI2", 2, "%", true),
            r(23, "Versão de Software", 2), r(30, "Temperatura do Mosfet", 1, "°C", true),
            r(33, "Temperatura do Mosfet 2", 1, "°C", true), r(34, "Temper. Ar Interno", 1, "°C", true),
            r(37, "Sobrecarga do Motor", 0, "%"), r(38, "Velocidade do Encoder", 0, "rpm"),
            r(39, "Contador Pulsos Encoder"), r(48, "Alarme Atual"), r(49, "Falha Atual"),
            r(50, "Última Falha"), r(54, "Segunda Falha"), r(58, "Terceira Falha"),
            r(62, "Quarta Falha"), r(66, "Quinta Falha"),
            r(90, "Corrente Últ. Falha", 1, "A"), r(91, "Tensão da Bateria Últ. Falha", 1, "V"),
            r(92, "Rotação Últ. Falha", 0, "rpm"), r(93, "Referência Últ. Falha", 0, "rpm"),
            r(94, "Frequência Últ. Falha", 1, "Hz"), r(95, "Tensão Motor Últ. Falha", 1, "V"),
            r(96, "Estado DIx Últ. Falha"), r(97, "Estado DOx Últ. Falha"),
            r(128, "Tempo para Atingir Velocidade", 1, "s"), r(295, "Corr. Nom/Pico Inv."),
            r(316, "Estado Interf. Serial"), r(680, "Estado Lógico"),
            r(681, "Velocidade 13 bits", 0, "", true), r(682, "Controle Serial"),
            r(683, "Ref. Vel. Serial", 0, "", true), r(684, "Controle CAN"),
            r(685, "Ref. Vel. CAN", 0, "", true), r(705, "Estado Controlador CAN"),
            r(706, "Telegramas CAN RX"), r(707, "Telegramas CAN TX"), r(708, "Contador de Bus Off"),
            r(709, "Mensagens CAN Perdidas"), r(1000, "Estado da SoftPLC"), r(1002, "Tempo Ciclo de Scan", 1, "ms")
        )
    }

    /** Parametros de acao/comando: ficam no backup mas nunca sao restaurados. */
    val naoRestaurar = setOf(0, 131, 200, 204, 303, 317, 408, 695, 696, 697, 1001)
}
