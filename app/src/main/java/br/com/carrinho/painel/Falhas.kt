package br.com.carrinho.painel

/** Falhas (F) e alarmes (A) do CVW300 — manual do usuario, referencia rapida. */
object Falhas {
    private val textos = mapOf(
        21 to "Subtensão Bateria",
        22 to "Sobretensão Bateria",
        30 to "Falha Braço U (curto mosfets)",
        34 to "Falha Braço V (curto mosfets)",
        38 to "Falha Braço W (curto mosfets)",
        46 to "Carga Alta no Motor",
        47 to "Carga Alta nos Mosfets",
        48 to "Sobrecarga nos Mosfets",
        50 to "Temperatura Alta nos Mosfets",
        51 to "Sobretemperatura Mosfets",
        67 to "Fiação Invertida Encoder/Motor",
        71 to "Sobrecorrente na Saída",
        72 to "Sobrecarga no Motor",
        78 to "Sobretemperatura Motor (PTC)",
        79 to "Falha Sinais Encoder",
        80 to "Falha na CPU (Watchdog)",
        90 to "Alarme Externo (DIx)",
        91 to "Falha Externa (DIx)",
        98 to "Ativar Habilita Geral (autoajuste)",
        99 to "Offset Corrente Inválido",
        110 to "Temperatura Alta no Motor (PTC)",
        128 to "Timeout Comunicação Serial",
        133 to "Sem Alimentação CAN",
        134 to "CAN Bus Off",
        135 to "Timeout Comunicação CAN-Automotivo",
        136 to "Mestre em Idle",
        150 to "Sobrevelocidade Motor",
        152 to "Temperatura Ar Interno Alta",
        153 to "Sobretemperatura Ar Interno",
        156 to "Subtemperatura",
        157 to "Perda Dados Tabela Parâmetros",
        158 to "Falha Tabela Parâmetros (padrão carregado)",
        182 to "Falha Realimentação de Pulsos",
        183 to "Sobrecarga Mosfets + Temperatura",
        185 to "Falha no Contator Principal",
        228 to "Timeout Comunicação Serial",
        234 to "Bus Off",
        235 to "Timeout Comunicação CAN-Automotivo",
        236 to "Mestre em Idle",
        702 to "Inversor Desabilitado (SoftPLC)",
        704 to "Dois Movimentos Habilitados",
        706 to "Referência não Programada para SoftPLC",
        711 to "Falha de Execução da SoftPLC"
    )

    fun descreve(prefixo: String, codigo: Int): String =
        "$prefixo${codigo.toString().padStart(3, '0')} ${textos[codigo] ?: "(ver manual)"}"
}
