package de.mchwrk.checkmyraspi.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatusScriptTest {

    private val pi4 = """
        Willkommen! Ausgabe aus ~/.ssh/rc, die ignoriert werden muss
        @@CMR:model
        Raspberry Pi 4 Model B Rev 1.4
        @@CMR:hostname
        keller-pi
        @@CMR:os
        PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
        @@CMR:kernel
        6.6.51+rpt-rpi-v8
        @@CMR:arch
        aarch64
        @@CMR:serial
        Serial		: 10000000a1b2c3d4
        @@CMR:uptime
        93784.52 360000.10
        @@CMR:loadavg
        0.42 0.31 0.25 1/245 12345
        @@CMR:nproc
        4
        @@CMR:meminfo
        MemTotal:        3884152 kB
        MemAvailable:    2942076 kB
        SwapTotal:        204796 kB
        SwapFree:         204796 kB
        @@CMR:temp
        48686
        @@CMR:throttled
        throttled=0x50005
        @@CMR:volts
        volt=0.8500V
        @@CMR:armfreq
        frequency(48)=1800404352
        @@CMR:firmware
        Mar 17 2023 10:52:42
        @@CMR:fan
        @@CMR:df
        Filesystem     1024-blocks    Used Available Capacity Mounted on
        /dev/mmcblk0p2    30358348 6123456  22943212      22% /
        /dev/mmcblk0p1      522232   63000    459232      13% /boot/firmware
        /dev/sda1        961302016 100 961301916       1% /mnt/usb platte
        @@CMR:net
        eth0 up dc:a6:32:01:02:03
        wlan0 down dc:a6:32:01:02:04
        docker0 down 02:42:ac:11:00:01
        veth1234 up 02:42:ac:11:00:09
        @@CMR:ip
        1: lo    inet 127.0.0.1/8 scope host lo\       valid_lft forever preferred_lft forever
        2: eth0    inet 192.168.1.50/24 brd 192.168.1.255 scope global dynamic noprefixroute eth0\       valid_lft 85000sec
        2: eth0    inet6 2a02:810d:1:2::50/64 scope global dynamic mngtmpaddr \       valid_lft 7000sec
        2: eth0    inet6 fe80::1/64 scope link \       valid_lft forever
        4: docker0    inet 172.17.0.1/16 brd 172.17.255.255 scope global docker0\       valid_lft forever
        @@CMR:wifi
        Inter-| sta-|   Quality        |   Discarded packets               | Missed | WE
         face | tus | link level noise |  nwid  crypt   frag  retry   misc | beacon | 22
         wlan0: 0000   62.  -48.  -256        0      0      0      0      0        0
        @@CMR:updates
        7
        @@CMR:reboot
        yes
        @@CMR:date
        2026-11-16 10:15:00 CET
        @@CMR:end
    """.trimIndent()

    @Test fun `parses basic system info and ignores noise before first marker`() {
        val s = StatusScript.parse(pi4)
        assertThat(s.model).isEqualTo("Raspberry Pi 4 Model B Rev 1.4")
        assertThat(s.isRaspberryPi).isTrue()
        assertThat(s.hostname).isEqualTo("keller-pi")
        assertThat(s.os).isEqualTo("Debian GNU/Linux 12 (bookworm)")
        assertThat(s.kernel).isEqualTo("6.6.51+rpt-rpi-v8")
        assertThat(s.arch).isEqualTo("aarch64")
        assertThat(s.serial).isEqualTo("10000000a1b2c3d4")
        assertThat(s.uptimeSeconds).isEqualTo(93784)
        assertThat(s.cpuCount).isEqualTo(4)
        assertThat(s.systemTime).isEqualTo("2026-11-16 10:15:00 CET")
    }

    @Test fun `parses load memory temperature and vcgencmd values`() {
        val s = StatusScript.parse(pi4)
        assertThat(s.load1).isEqualTo(0.42)
        assertThat(s.load15).isEqualTo(0.25)
        assertThat(s.memTotalKb).isEqualTo(3884152)
        assertThat(s.memAvailableKb).isEqualTo(2942076)
        assertThat(s.memUsedPercent).isEqualTo(24)
        assertThat(s.cpuTempC).isWithin(0.001).of(48.686)
        assertThat(s.coreVolts).isEqualTo(0.85)
        assertThat(s.armFreqHz).isEqualTo(1800404352)
        assertThat(s.firmware).isEqualTo("Mar 17 2023 10:52:42")
        assertThat(s.fanRpm).isNull()
    }

    @Test fun `decodes throttle bits`() {
        val t = StatusScript.parse(pi4).throttle!!
        assertThat(t.underVoltageNow).isTrue()
        assertThat(t.throttledNow).isTrue()
        assertThat(t.freqCappedNow).isFalse()
        assertThat(t.underVoltageOccurred).isTrue()
        assertThat(t.throttledOccurred).isTrue()
        assertThat(t.anyNow).isTrue()
        assertThat(Throttle(0).ok).isTrue()
        assertThat(Throttle(0x50000).anyNow).isFalse()
        assertThat(Throttle(0x50000).anyOccurred).isTrue()
    }

    @Test fun `parses disks including mount points with spaces`() {
        val d = StatusScript.parse(pi4).disks
        assertThat(d.map { it.mount }).containsExactly("/", "/boot/firmware", "/mnt/usb platte").inOrder()
        assertThat(d[0].usedPercent).isEqualTo(20)
        assertThat(StatusScript.parse(pi4).rootDisk!!.mount).isEqualTo("/")
    }

    @Test fun `parses interfaces addresses and wifi`() {
        val s = StatusScript.parse(pi4)
        val names = s.interfaces.map { it.name }
        assertThat(names).containsExactly("eth0", "wlan0", "docker0").inOrder()
        val eth = s.interfaces.first { it.name == "eth0" }
        assertThat(eth.isUp).isTrue()
        assertThat(eth.ipv4).containsExactly("192.168.1.50")
        assertThat(eth.ipv6).containsExactly("2a02:810d:1:2::50")
        val wlan = s.interfaces.first { it.name == "wlan0" }
        assertThat(wlan.wifiQuality).isEqualTo(62)
        assertThat(wlan.wifiSignalDbm).isEqualTo(-48)
        assertThat(s.primaryMac).isEqualTo("dc:a6:32:01:02:03")
    }

    @Test fun `parses updates and reboot flag`() {
        val s = StatusScript.parse(pi4)
        assertThat(s.upgradable).isEqualTo(7)
        assertThat(s.rebootRequired).isTrue()
    }

    @Test fun `generic linux without vcgencmd and apt yields nulls not errors`() {
        val out = """
            @@CMR:model
            @@CMR:hostname
            nas
            @@CMR:temp
            @@CMR:reboot
            @@CMR:end
        """.trimIndent()
        val s = StatusScript.parse(out)
        assertThat(s.hostname).isEqualTo("nas")
        assertThat(s.model).isNull()
        assertThat(s.isRaspberryPi).isFalse()
        assertThat(s.throttle).isNull()
        assertThat(s.upgradable).isNull()
        assertThat(s.rebootRequired).isFalse()
        assertThat(s.cpuTempC).isNull()
    }

    @Test fun `serial from device tree has no colon`() {
        val s = StatusScript.parse("@@CMR:serial\n6a1b2c3d4e5f6a7b\n@@CMR:end")
        assertThat(s.serial).isEqualTo("6a1b2c3d4e5f6a7b")
    }

    @Test fun `handles CRLF line endings`() {
        val s = StatusScript.parse("@@CMR:hostname\r\npi\r\n@@CMR:end\r\n")
        assertThat(s.hostname).isEqualTo("pi")
    }

    @Test fun `script uses only markers the parser knows`() {
        val names = Regex("(?m)\\bm ([a-z]+)(;|$)").findAll(StatusScript.script).map { it.groupValues[1] }.toList()
        assertThat(names).containsAtLeast("model", "throttled", "df", "net", "updates", "end")
    }

    @Test fun `script runs in a real posix shell and parses`() {
        val sh = java.io.File("/bin/sh")
        org.junit.Assume.assumeTrue(sh.canExecute())
        val p = ProcessBuilder("sh", "-s").redirectErrorStream(false).start()
        p.outputStream.use { it.write(StatusScript.script.toByteArray()) }
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val sections = StatusScript.sections(out)
        assertThat(sections.keys).containsAtLeast("hostname", "kernel", "meminfo", "df", "end")
        val s = StatusScript.parse(out)
        assertThat(s.hostname).isNotEmpty()
        assertThat(s.memTotalKb).isGreaterThan(0L)
        assertThat(s.rootDisk).isNotNull()
        assertThat(s.interfaces).isNotEmpty()
    }

    @Test fun `socket errors become plain german messages with ip`() {
        val noRoute = java.net.ConnectException("failed to connect to casanawie/192.168.178.83 (port 22) from /192.168.178.20 (port 48250) after 8000ms: isConnected failed: EHOSTUNREACH (No route to host)")
        assertThat(unreachableMessage(noRoute, 22)).isEqualTo("Gerät antwortet nicht (192.168.178.83) – ausgeschaltet oder in einem anderen Netz?")
        val refused = java.net.ConnectException("failed to connect to pi/10.0.0.5 (port 22) from /10.0.0.2 (port 1) after 3ms: isConnected failed: ECONNREFUSED (Connection refused)")
        assertThat(unreachableMessage(refused, 22)).isEqualTo("Gerät erreichbar (10.0.0.5), aber SSH auf Port 22 ist nicht aktiv.")
        assertThat(unreachableMessage(java.net.ConnectException("Connection refused"), 2222)).isEqualTo("Gerät erreichbar, aber SSH auf Port 2222 ist nicht aktiv.")
        assertThat(unreachableMessage(java.net.SocketTimeoutException("connect timed out"), 22)).startsWith("Keine Antwort – Zeitüberschreitung")
    }

    @Test fun `sha256 fingerprint uses openssh format`() {
        // echo -n "abc" | sha256sum → ba7816bf…; Base64 ohne Padding
        assertThat(Fingerprints.sha256("abc".toByteArray()))
            .isEqualTo("SHA256:ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0")
    }

    @Test fun `hanging ssh read timeout gets a plain message`() {
        val m = transportMessage(java.io.IOException("x", java.net.SocketTimeoutException("Read timed out")))
        assertThat(m).startsWith("SSH antwortet nicht")
        assertThat(transportMessage(java.io.IOException("Broken pipe"))).isEqualTo("Verbindungsfehler: Broken pipe")
    }
}
