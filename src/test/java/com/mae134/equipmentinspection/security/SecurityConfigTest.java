package com.mae134.equipmentinspection.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.equipment.EquipmentRequest;
import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private EquipmentService equipmentService;

  @Autowired private EquipmentRepository equipmentRepository;

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

  @Test
  void unauthenticatedUserCannotPerformProtectedUpdate() throws Exception {
    mockMvc.perform(post("/api/equipment").with(csrf())).andExpect(status().is3xxRedirection());
  }

  @Test
  void unauthenticatedUserCannotAccessDashboard() throws Exception {
    mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
  }

  @Test
  void authenticatedUserCanAccessDashboard() throws Exception {
    mockMvc
        .perform(get("/dashboard").with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanAccessDashboard() throws Exception {
    mockMvc
        .perform(get("/dashboard").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanAccessEquipmentCreatePage() throws Exception {
    mockMvc
        .perform(get("/equipment/new").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void inspectorCannotAccessEquipmentCreatePage() throws Exception {
    mockMvc
        .perform(get("/equipment/new").with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void inspectorCannotCreateEquipment() throws Exception {
    mockMvc
        .perform(
            post("/equipment")
                .with(user("inspector@example.com").roles("INSPECTOR"))
                .with(csrf())
                .param("equipmentCode", "EQ-TEST-403")
                .param("name", "認可テスト設備"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedUserCannotAccessEquipmentCreatePage() throws Exception {
    mockMvc
        .perform(get("/equipment/new"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void adminCannotCreateEquipmentWithoutRequiredFields() throws Exception {
    mockMvc
        .perform(
            post("/equipment")
                .with(user("admin@example.com").roles("ADMIN"))
                .with(csrf())
                .param("equipmentCode", "")
                .param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("equipment-create"))
        .andExpect(model().attributeHasFieldErrors("equipmentForm", "equipmentCode", "name"));
  }

  @Test
  void adminCanAccessEquipmentDetailPage() throws Exception {
    mockMvc
        .perform(get("/equipment/999999999").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  void inspectorCanAccessEquipmentDetailPage() throws Exception {
    mockMvc
        .perform(get("/equipment/999999999").with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedUserCannotAccessEquipmentDetailPage() throws Exception {
    mockMvc
        .perform(get("/equipment/999999999"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void otherRoleCannotAccessEquipmentDetailPage() throws Exception {
    mockMvc
        .perform(get("/equipment/999999999").with(user("viewer@example.com").roles("VIEWER")))
        .andExpect(status().isForbidden());
  }

  private EquipmentResponse createTestEquipment() {
    EquipmentRequest request =
        new EquipmentRequest(
            "TEST-DETAIL-152",
            "詳細画面テスト設備",
            "テストメーカー",
            "MODEL-152",
            "工場A",
            LocalDate.of(2026, 11, 16),
            30,
            "詳細画面の表示確認",
            true);

    return equipmentService.save(request);
  }

  @Test
  void adminCanViewEquipmentDetailPage() throws Exception {
    EquipmentResponse equipment = createTestEquipment();

    try {
      mockMvc
          .perform(
              get("/equipment/{equipmentId}", equipment.id())
                  .with(user("admin@example.com").roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(view().name("equipment-detail"))
          .andExpect(model().attributeExists("equipment"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("詳細画面テスト設備")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("TEST-DETAIL-152")));
    } finally {
      equipmentRepository.deleteById(equipment.id());
    }
  }

  @Test
  void inspectorCanViewEquipmentDetailPage() throws Exception {
    EquipmentResponse equipment = createTestEquipment();

    try {
      mockMvc
          .perform(
              get("/equipment/{equipmentId}", equipment.id())
                  .with(user("inspector@example.com").roles("INSPECTOR")))
          .andExpect(status().isOk())
          .andExpect(view().name("equipment-detail"))
          .andExpect(model().attributeExists("equipment"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("詳細画面テスト設備")));
    } finally {
      equipmentRepository.deleteById(equipment.id());
    }
  }

  @Test
  void adminCanAccessInspectionItemSettingsPage() throws Exception {
    mockMvc
        .perform(
            get("/equipment/1/inspection-items").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("inspection-item-settings"))
        .andExpect(model().attributeExists("equipment", "inspectionItems"));
  }

  @Test
  void inspectorCannotAccessInspectionItemSettingsPage() throws Exception {
    mockMvc
        .perform(
            get("/equipment/1/inspection-items")
                .with(user("inspector@example.com").roles("INSPECTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedUserCannotAccessInspectionItemSettingsPage() throws Exception {
    mockMvc
        .perform(get("/equipment/1/inspection-items"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }
}
