package com.libreria.sistema.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.libreria.sistema.service.LicenseValidationService;
import com.libreria.sistema.model.SystemConfiguration;
import com.libreria.sistema.repository.SystemConfigurationRepository;
import com.libreria.sistema.service.ConexionMovilService;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conexion-movil-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc
class ConexionMovilControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LicenseValidationService licenseValidationService;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void vistaConexionMovilMuestraDiagnosticoDockerWindows() throws Exception {
        licenciaActiva();

        mockMvc.perform(get("/conexion-movil"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conexión Móvil")))
                .andExpect(content().string(containsString("IP para el celular")))
                .andExpect(content().string(containsString("/conexion-movil/acceso")))
                .andExpect(content().string(containsString("Prueba rápida desde celular")))
                .andExpect(content().string(containsString("Último acceso detectado")))
                .andExpect(content().string(containsString("Test-NetConnection desde esta PC no prueba el celular")))
                .andExpect(content().string(containsString("Test-NetConnection")))
                .andExpect(content().string(containsString("netsh advfirewall")))
                .andExpect(content().string(containsString("Docker Desktop para Windows")))
                .andExpect(content().string(containsString("192.168.65.x")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void noPermiteGuardarIpInternaDeDockerComoIpMovil() throws Exception {
        licenciaActiva();

        mockMvc.perform(post("/conexion-movil/guardar-ip")
                        .param("ip", "192.168.65.254"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("Docker")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void pausaQrCuandoLaIpGuardadaEsInternaDeDocker() throws Exception {
        licenciaActiva();
        systemConfigurationRepository.save(new SystemConfiguration(ConexionMovilService.KEY_HOST_IP, "192.168.65.254"));

        mockMvc.perform(get("/conexion-movil"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QR pausado")))
                .andExpect(content().string(containsString("Configure una IP real para activar el QR")))
                .andExpect(content().string(containsString("disabled=\"disabled\"")));
    }

    @Test
    void pingMovilTxtRespondeSinLogin() throws Exception {
        mockMvc.perform(get("/conexion-movil/ping.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OK - Sistema accesible desde celular")));
    }

    @Test
    void pingMovilHtmlRespondeSinLogin() throws Exception {
        mockMvc.perform(get("/conexion-movil/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El celular llega al sistema")))
                .andExpect(content().string(containsString("Entrar al sistema")));
    }

    @Test
    void accesoMovilRespondeSinLogin() throws Exception {
        mockMvc.perform(get("/conexion-movil/acceso"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("celular")))
                .andExpect(content().string(containsString("Entrar al sistema")))
                .andExpect(content().string(containsString("Prueba en texto")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void apiUltimoAccesoMuestraSolicitudMovilRegistrada() throws Exception {
        licenciaActiva();

        mockMvc.perform(get("/conexion-movil/acceso")
                        .header("User-Agent", "Android Chrome"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/conexion-movil/api/ultimo-acceso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true))
                .andExpect(jsonPath("$.ruta").value("/conexion-movil/acceso"));
    }

    private void licenciaActiva() {
        LicenseValidationService.LicenseInfo info = new LicenseValidationService.LicenseInfo();
        info.setEstado(LicenseValidationService.EstadoLicencia.ACTIVO);
        info.setFechaVencimiento(LocalDate.now().plusDays(30));
        info.setNivelPlan("TEST");
        info.setDiasRestantes(30);
        info.setMensaje("Licencia activa para prueba");
        when(licenseValidationService.validarLicencia()).thenReturn(info);
    }
}
