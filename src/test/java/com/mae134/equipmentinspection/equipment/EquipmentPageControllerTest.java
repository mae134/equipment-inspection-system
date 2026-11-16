package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class EquipmentPageControllerTest {

  @Mock private EquipmentService equipmentService;

  private EquipmentPageController controller;

  @BeforeEach
  void setUp() {
    controller = new EquipmentPageController(equipmentService);
  }

  @Test
  void showCreateFormShouldDisplayEquipmentForm() {
    Model model = new ConcurrentModel();

    String viewName = controller.showCreateForm(model);

    assertEquals("equipment-create", viewName);
    assertNotNull(model.getAttribute("equipmentForm"));
  }

  @Test
  void createEquipmentShouldRedirectToDashboard() {
    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-003");
    form.setName("テスト設備");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String viewName = controller.createEquipment(form, bindingResult, redirectAttributes);

    assertEquals("redirect:/dashboard", viewName);
    assertEquals("設備を登録しました", redirectAttributes.getFlashAttributes().get("successMessage"));

    verify(equipmentService).save(any(EquipmentRequest.class));
  }

  @Test
  void createEquipmentShouldShowErrorWhenEquipmentCodeIsDuplicated() {
    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-002");
    form.setName("重複テスト設備");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    doThrow(new DuplicateResourceException("設備コードは既に登録されています"))
        .when(equipmentService)
        .save(any(EquipmentRequest.class));

    String viewName = controller.createEquipment(form, bindingResult, redirectAttributes);

    assertEquals("equipment-create", viewName);
    assertTrue(bindingResult.hasFieldErrors("equipmentCode"));

    assertEquals(
        "設備コードは既に登録されています", bindingResult.getFieldError("equipmentCode").getDefaultMessage());
  }

  @Test
  void showEquipmentDetailShouldDisplayEquipmentDetail() {
    Long equipmentId = 1L;
    Model model = new ConcurrentModel();

    EquipmentResponse equipment =
        new EquipmentResponse(
            equipmentId,
            "EQ-001",
            "テスト設備",
            "テストメーカー",
            "MODEL-001",
            "第一工場",
            LocalDate.of(2026, 1, 1),
            30,
            "テスト用設備",
            true,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            LocalDateTime.of(2026, 1, 2, 10, 0));

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    String viewName = controller.showEquipmentDetail(equipmentId, model);

    assertEquals("equipment-detail", viewName);
    assertSame(equipment, model.getAttribute("equipment"));

    verify(equipmentService).findById(equipmentId);
  }

  @Test
  void showEquipmentDetailShouldThrowNotFoundWhenEquipmentDoesNotExist() {
    Long equipmentId = 999L;
    Model model = new ConcurrentModel();

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.showEquipmentDetail(equipmentId, model));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    verify(equipmentService).findById(equipmentId);
  }
}
