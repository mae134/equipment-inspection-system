package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

  @Test
  void showEditFormShouldDisplayExistingEquipment() {
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
            false,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            LocalDateTime.of(2026, 1, 2, 10, 0));

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    String viewName = controller.showEditForm(equipmentId, model);

    assertEquals("equipment-edit", viewName);
    assertEquals(equipmentId, model.getAttribute("equipmentId"));

    EquipmentForm form = (EquipmentForm) model.getAttribute("equipmentForm");

    assertNotNull(form);
    assertEquals("EQ-001", form.getEquipmentCode());
    assertEquals("テスト設備", form.getName());
    assertEquals("テストメーカー", form.getManufacturer());
    assertEquals("MODEL-001", form.getModel());
    assertEquals("第一工場", form.getLocation());
    assertEquals(LocalDate.of(2026, 1, 1), form.getInstalledDate());
    assertEquals(30, form.getInspectionCycleDays());
    assertEquals("テスト用設備", form.getDescription());

    verify(equipmentService).findById(equipmentId);
  }

  @Test
  void showEditFormShouldThrowNotFoundWhenEquipmentDoesNotExist() {
    Long equipmentId = 999L;
    Model model = new ConcurrentModel();

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.showEditForm(equipmentId, model));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    verify(equipmentService).findById(equipmentId);
  }

  @Test
  void updateEquipmentShouldPreserveActiveAndRedirectToDetail() {
    Long equipmentId = 1L;

    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-001");
    form.setName("更新後の設備名");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse existingEquipment =
        new EquipmentResponse(
            equipmentId,
            "EQ-001",
            "更新前の設備名",
            "テストメーカー",
            "MODEL-001",
            "第一工場",
            LocalDate.of(2026, 1, 1),
            30,
            "テスト用設備",
            false,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            LocalDateTime.of(2026, 1, 2, 10, 0));

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(existingEquipment));

    String viewName =
        controller.updateEquipment(equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("redirect:/equipment/1", viewName);
    assertEquals("設備を更新しました", redirectAttributes.getFlashAttributes().get("successMessage"));

    verify(equipmentService)
        .update(
            eq(equipmentId),
            argThat(
                request ->
                    !request.active()
                        && "EQ-001".equals(request.equipmentCode())
                        && "更新後の設備名".equals(request.name())));
  }

  @Test
  void updateEquipmentShouldShowEditFormWhenValidationFails() {
    Long equipmentId = 1L;

    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-001");
    form.setName("");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");
    bindingResult.rejectValue("name", "NotBlank", "設備名は必須です");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse existingEquipment = createEquipmentResponse(equipmentId);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(existingEquipment));

    String viewName =
        controller.updateEquipment(equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("equipment-edit", viewName);
    assertEquals(equipmentId, model.getAttribute("equipmentId"));
    assertTrue(bindingResult.hasFieldErrors("name"));

    verify(equipmentService, never()).update(any(Long.class), any(EquipmentRequest.class));
  }

  @Test
  void updateEquipmentShouldShowErrorWhenEquipmentCodeIsDuplicated() {
    Long equipmentId = 1L;

    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-002");
    form.setName("更新後の設備名");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    when(equipmentService.findById(equipmentId))
        .thenReturn(Optional.of(createEquipmentResponse(equipmentId)));

    doThrow(new DuplicateResourceException("設備コードは既に登録されています"))
        .when(equipmentService)
        .update(eq(equipmentId), any(EquipmentRequest.class));

    String viewName =
        controller.updateEquipment(equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("equipment-edit", viewName);
    assertTrue(bindingResult.hasFieldErrors("equipmentCode"));
    assertEquals(
        "設備コードは既に登録されています", bindingResult.getFieldError("equipmentCode").getDefaultMessage());
  }

  @Test
  void updateEquipmentShouldThrowNotFoundWhenEquipmentDoesNotExist() {
    Long equipmentId = 999L;

    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-999");
    form.setName("存在しない設備");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    Model model = new ConcurrentModel();

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.updateEquipment(
                    equipmentId, form, bindingResult, model, new RedirectAttributesModelMap()));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    verify(equipmentService, never()).update(any(Long.class), any(EquipmentRequest.class));
  }

  private EquipmentResponse createEquipmentResponse(Long equipmentId) {
    return new EquipmentResponse(
        equipmentId,
        "EQ-001",
        "テスト設備",
        "テストメーカー",
        "MODEL-001",
        "第一工場",
        LocalDate.of(2026, 1, 1),
        30,
        "テスト用設備",
        false,
        LocalDateTime.of(2026, 1, 1, 10, 0),
        LocalDateTime.of(2026, 1, 2, 10, 0));
  }
}
