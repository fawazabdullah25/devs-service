package org.kstacks.devs.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class LockedSecurityTests {
    private MockMvc mockMvc;
    @Autowired private WebApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void lockedAdminRequestsAreForbiddenWithoutABasicAuthChallenge() throws Exception {
        mockMvc.perform(get("/devs/api/v1/admin/content"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void corsPreflightAllowsPutEvenWhenAdminEndpointsAreLocked() throws Exception {
        mockMvc.perform(options("/devs/api/v1/admin/content/{id}/curriculum", "00000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")));
    }

    @Test
    void anErrorDispatchIsNotConvertedIntoAnAuthenticationChallenge() throws Exception {
        mockMvc.perform(put("/devs/api/v1/admin/content/{id}/curriculum", "00000000-0000-0000-0000-000000000001")
                .with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                })
                .contentType("application/json")
                .content("{\"sections\":[],\"unsectionedUnitIds\":[]}"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void providerWebhookRoutesAreNotPublic() throws Exception {
        mockMvc.perform(post("/devs/api/v1/webhooks/provider"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
}
