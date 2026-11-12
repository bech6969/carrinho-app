package br.com.carrinho.painel

import com.hoho.android.usbserial.driver.UsbSerialPort

/** Mestre Modbus RTU minimo (funcoes 03 e 06) sobre porta serial USB. */
class ModbusRtu(private val port: UsbSerialPort, private val slave: Int) {

    private val timeoutMs = 300

    /** Le [count] registradores a partir de [addr]. Retorna null em erro. */
    @Synchronized
    fun readHolding(addr: Int, count: Int): IntArray? {
        val pdu = byteArrayOf(
            slave.toByte(), 0x03,
            (addr shr 8).toByte(), addr.toByte(),
            (count shr 8).toByte(), count.toByte()
        )
        val resp = transact(pdu, 5 + 2 * count) ?: return null
        if (resp[1].toInt() != 0x03 || (resp[2].toInt() and 0xFF) != 2 * count) return null
        return IntArray(count) { i ->
            ((resp[3 + 2 * i].toInt() and 0xFF) shl 8) or (resp[4 + 2 * i].toInt() and 0xFF)
        }
    }

    /** Escreve um registrador. */
    @Synchronized
    fun writeSingle(addr: Int, value: Int): Boolean {
        val pdu = byteArrayOf(
            slave.toByte(), 0x06,
            (addr shr 8).toByte(), addr.toByte(),
            (value shr 8).toByte(), value.toByte()
        )
        return transact(pdu, 8) != null
    }

    private fun transact(pdu: ByteArray, expected: Int): ByteArray? {
        val crc = crc16(pdu, pdu.size)
        val frame = pdu + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())

        descartaEntrada()
        port.write(frame, timeoutMs)

        val buf = ByteArray(256)
        val tmp = ByteArray(64)
        var n = 0
        val limite = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < limite && n < expected) {
            val r = port.read(tmp, 50)
            if (r > 0) {
                val copia = minOf(r, buf.size - n)
                System.arraycopy(tmp, 0, buf, n, copia)
                n += copia
                if (n >= 5 && (buf[1].toInt() and 0x80) != 0) return null // resposta de excecao
            }
        }
        if (n < expected) return null
        if ((buf[0].toInt() and 0xFF) != slave) return null

        val c = crc16(buf, expected - 2)
        val ok = (buf[expected - 2].toInt() and 0xFF) == (c and 0xFF) &&
                 (buf[expected - 1].toInt() and 0xFF) == ((c shr 8) and 0xFF)
        return if (ok) buf.copyOf(expected) else null
    }

    private fun descartaEntrada() {
        try { port.purgeHwBuffers(true, true) } catch (_: Exception) { }
        val t = ByteArray(64)
        try { while (port.read(t, 5) > 0) { } } catch (_: Exception) { }
    }

    companion object {
        fun crc16(d: ByteArray, len: Int): Int {
            var crc = 0xFFFF
            for (i in 0 until len) {
                crc = crc xor (d[i].toInt() and 0xFF)
                repeat(8) {
                    crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
                }
            }
            return crc
        }
    }
}
