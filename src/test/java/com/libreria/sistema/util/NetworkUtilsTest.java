package com.libreria.sistema.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkUtilsTest {

    @Test
    void marcaIpDockerDesktopComoNoAptaParaCelular() {
        assertThat(NetworkUtils.esIpInternaDocker("192.168.65.254")).isTrue();
        assertThat(NetworkUtils.esIpAccesibleDesdeMovil("192.168.65.254")).isFalse();
    }

    @Test
    void aceptaIpLanRealDeWindows() {
        assertThat(NetworkUtils.esIpInternaDocker("192.168.18.11")).isFalse();
        assertThat(NetworkUtils.esIpAccesibleDesdeMovil("192.168.18.11")).isTrue();
    }

    @Test
    void rechazaIpv4FueraDeRango() {
        assertThat(NetworkUtils.esIpv4Valida("999.999.999.999")).isFalse();
        assertThat(NetworkUtils.isPrivateIp("999.999.999.999")).isFalse();
    }
}
