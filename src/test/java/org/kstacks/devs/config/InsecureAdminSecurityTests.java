package org.kstacks.devs.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "devs.security.allow-insecure-admin=true")
class InsecureAdminSecurityTests {
    private MockMvc mockMvc;
    @Autowired private WebApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void insecureAdminModeAllowsAdminRequestsForLocalStaging() throws Exception {
        mockMvc.perform(get("/devs/api/v1/admin/content"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void anAdminErrorResponseRemainsTheRealNotFoundResponse() throws Exception {
        mockMvc.perform(put("/devs/api/v1/admin/content/{id}/curriculum", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sections\":[],\"unsectionedUnitIds\":[]}"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
}
