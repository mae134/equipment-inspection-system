package com.mae134.equipmentinspection.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedUserCannotAccessProtectedApi() throws Exception {
    mockMvc.perform(get("/api/equipment")).andExpect(status().is3xxRedirection());
  }

  @Test
  void inspectorCanAccessInspectionScreen() throws Exception {
    mockMvc
        .perform(
            get("/equipment/1/inspections/new")
                .with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isOk());
  }

  @Test
  void inspectorCannotAccessAdminApi() throws Exception {
    mockMvc
        .perform(get("/api/equipment").with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanAccessAdminApi() throws Exception {
    mockMvc
        .perform(get("/api/equipment").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void authenticatedUserCanLogout() throws Exception {
    mockMvc
        .perform(logout())
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?logout"));
  }
}
