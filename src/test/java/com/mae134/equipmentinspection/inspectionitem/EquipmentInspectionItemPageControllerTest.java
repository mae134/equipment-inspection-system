package com.mae134.equipmentinspection.inspectionitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
class EquipmentInspectionItemPageControllerTest {

  @Mock private EquipmentService equipmentService;

  @Mock private EquipmentInspectionItemService inspectionItemService;

  private EquipmentInspectionItemPageController controller;

  @BeforeEach
  void setUp() {
    controller = new EquipmentInspectionItemPageController(equipmentService, inspectionItemService);
  }

  @Test
  void showInspectionItemSettingsShouldDisplaySettingsPage() {
    Long equipmentId = 1L;
    Model model = new ConcurrentModel();

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of());

    String viewName = controller.showInspectionItemSettings(equipmentId, model);

    assertEquals("inspection-item-settings", viewName);
    assertEquals(equipment, model.getAttribute("equipment"));
    assertEquals(List.of(), model.getAttribute("inspectionItems"));
    assertNotNull(model.getAttribute("inspectionItemForm"));
  }

  @Test
  void showInspectionItemSettingsShouldThrowNotFoundWhenEquipmentDoesNotExist() {
    Long equipmentId = 999L;
    Model model = new ConcurrentModel();

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.showInspectionItemSettings(equipmentId, model));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void createInspectionItemShouldRedirectToSettingsPage() {
    Long equipmentId = 1L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setUnit("℃");
    form.setDisplayOrder(1);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String viewName =
        controller.createInspectionItem(
            equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("redirect:/equipment/1/inspection-items", viewName);
    assertEquals("点検項目を登録しました", redirectAttributes.getFlashAttributes().get("successMessage"));

    verify(inspectionItemService).save(any(EquipmentInspectionItemRequest.class));
  }

  @Test
  void createInspectionItemShouldShowErrorWhenNameIsDuplicated() {
    Long equipmentId = 1L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setDisplayOrder(1);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of());

    doThrow(new DuplicateResourceException("点検項目名は既に登録されています"))
        .when(inspectionItemService)
        .save(any(EquipmentInspectionItemRequest.class));

    String viewName =
        controller.createInspectionItem(
            equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertTrue(bindingResult.hasFieldErrors("name"));
    assertEquals("点検項目名は既に登録されています", bindingResult.getFieldError("name").getDefaultMessage());
  }

  @Test
  void createInspectionItemShouldNotSaveWhenValidationFails() {
    Long equipmentId = 1L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    bindingResult.rejectValue("name", "NotBlank", "点検項目名を入力してください");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of());

    String viewName =
        controller.createInspectionItem(
            equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertTrue(bindingResult.hasFieldErrors("name"));

    verify(inspectionItemService, never()).save(any(EquipmentInspectionItemRequest.class));
  }

  @Test
  void createInspectionItemShouldShowErrorWhenSettingsAreInvalid() {
    Long equipmentId = 1L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of());

    doThrow(new IllegalArgumentException("最小値は最大値以下にしてください"))
        .when(inspectionItemService)
        .save(any(EquipmentInspectionItemRequest.class));

    String viewName =
        controller.createInspectionItem(
            equipmentId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertTrue(bindingResult.hasGlobalErrors());
    assertEquals("最小値は最大値以下にしてください", bindingResult.getGlobalError().getDefaultMessage());
  }

  @Test
  void showEditInspectionItemShouldDisplayEditForm() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    EquipmentInspectionItemResponse item =
        new EquipmentInspectionItemResponse(
            itemId,
            equipmentId,
            "モーター温度",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("0"),
            new BigDecimal("80"),
            null,
            "モーターの温度を点検",
            1,
            true,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            LocalDateTime.of(2026, 1, 2, 10, 0));

    Model model = new ConcurrentModel();

    // Arrange：モック設定
    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of(item));

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    // Act：Controller実行
    String viewName = controller.showEditInspectionItem(equipmentId, itemId, model);

    // Assert：結果検証
    assertEquals("inspection-item-settings", viewName);
    assertEquals(equipment, model.getAttribute("equipment"));
    assertEquals(List.of(item), model.getAttribute("inspectionItems"));
    assertEquals(itemId, model.getAttribute("editingItemId"));

    EquipmentInspectionItemForm form =
        assertInstanceOf(
            EquipmentInspectionItemForm.class, model.getAttribute("inspectionItemForm"));

    assertNotNull(form);
    assertEquals("モーター温度", form.getName());
    assertEquals(InspectionItemType.NUMERIC, form.getType());
    assertEquals("℃", form.getUnit());
    assertEquals(new BigDecimal("0"), form.getMinValue());
    assertEquals(new BigDecimal("80"), form.getMaxValue());
    assertEquals(1, form.getDisplayOrder());
    assertEquals(true, form.getActive());
  }

  @Test
  void showEditInspectionItemShouldThrowNotFoundWhenItemBelongsToAnotherEquipment() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentResponse equipment = createTestEquipment(equipmentId);

    EquipmentInspectionItemResponse item =
        new EquipmentInspectionItemResponse(
            itemId,
            2L, // 別の設備ID
            "モーター温度",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("0"),
            new BigDecimal("80"),
            null,
            "モーターの温度を点検",
            1,
            true,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            LocalDateTime.of(2026, 1, 2, 10, 0));

    Model model = new ConcurrentModel();

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of());

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.showEditInspectionItem(equipmentId, itemId, model));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void updateInspectionItemShouldRedirectToSettingsPage() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度（更新）");
    form.setType(InspectionItemType.NUMERIC);
    form.setUnit("℃");
    form.setDisplayOrder(1);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentInspectionItemResponse item = createTestInspectionItem(itemId, equipmentId);

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    String viewName =
        controller.updateInspectionItem(
            equipmentId, itemId, form, bindingResult, model, redirectAttributes);

    assertEquals("redirect:/equipment/1/inspection-items", viewName);
    assertEquals("点検項目を更新しました", redirectAttributes.getFlashAttributes().get("successMessage"));

    verify(inspectionItemService)
        .update(org.mockito.ArgumentMatchers.eq(itemId), any(EquipmentInspectionItemRequest.class));
  }

  @Test
  void updateInspectionItemShouldShowErrorWhenNameIsDuplicated() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);
    EquipmentInspectionItemResponse item = createTestInspectionItem(itemId, equipmentId);

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of(item));

    doThrow(new DuplicateResourceException("点検項目名は既に登録されています"))
        .when(inspectionItemService)
        .update(eq(itemId), any(EquipmentInspectionItemRequest.class));

    String viewName =
        controller.updateInspectionItem(
            equipmentId, itemId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertEquals(itemId, model.getAttribute("editingItemId"));
    assertTrue(bindingResult.hasFieldErrors("name"));
    assertEquals("点検項目名は既に登録されています", bindingResult.getFieldError("name").getDefaultMessage());

    verify(inspectionItemService).update(eq(itemId), any(EquipmentInspectionItemRequest.class));
  }

  @Test
  void updateInspectionItemShouldNotUpdateWhenValidationFails() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    bindingResult.rejectValue("name", "NotBlank", "点検項目名を入力してください");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);
    EquipmentInspectionItemResponse item = createTestInspectionItem(itemId, equipmentId);

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of(item));

    String viewName =
        controller.updateInspectionItem(
            equipmentId, itemId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertEquals(itemId, model.getAttribute("editingItemId"));
    assertTrue(bindingResult.hasFieldErrors("name"));

    verify(inspectionItemService, never())
        .update(eq(itemId), any(EquipmentInspectionItemRequest.class));
  }

  @Test
  void updateInspectionItemShouldShowErrorWhenSettingsAreInvalid() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    EquipmentResponse equipment = createTestEquipment(equipmentId);
    EquipmentInspectionItemResponse item = createTestInspectionItem(itemId, equipmentId);

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    when(equipmentService.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(inspectionItemService.findByEquipmentId(equipmentId)).thenReturn(List.of(item));

    doThrow(new IllegalArgumentException("最小値は最大値以下にしてください"))
        .when(inspectionItemService)
        .update(eq(itemId), any(EquipmentInspectionItemRequest.class));

    String viewName =
        controller.updateInspectionItem(
            equipmentId, itemId, form, bindingResult, model, redirectAttributes);

    assertEquals("inspection-item-settings", viewName);
    assertEquals(itemId, model.getAttribute("editingItemId"));
    assertTrue(bindingResult.hasGlobalErrors());
    assertEquals("最小値は最大値以下にしてください", bindingResult.getGlobalError().getDefaultMessage());
  }

  @Test
  void updateInspectionItemShouldThrowNotFoundWhenItemBelongsToAnotherEquipment() {
    Long equipmentId = 1L;
    Long itemId = 10L;

    EquipmentInspectionItemForm form = new EquipmentInspectionItemForm();
    form.setName("モーター温度");
    form.setType(InspectionItemType.NUMERIC);
    form.setActive(true);

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "inspectionItemForm");

    Model model = new ConcurrentModel();
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    // 点検項目は設備ID=2に所属している
    EquipmentInspectionItemResponse item = createTestInspectionItem(itemId, 2L);

    when(inspectionItemService.findById(itemId)).thenReturn(item);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.updateInspectionItem(
                    equipmentId, itemId, form, bindingResult, model, redirectAttributes));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    verify(inspectionItemService, never())
        .update(eq(itemId), any(EquipmentInspectionItemRequest.class));
  }

  private EquipmentResponse createTestEquipment(Long equipmentId) {
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
        true,
        LocalDateTime.of(2026, 1, 1, 10, 0),
        LocalDateTime.of(2026, 1, 2, 10, 0));
  }

  private EquipmentInspectionItemResponse createTestInspectionItem(Long itemId, Long equipmentId) {
    return new EquipmentInspectionItemResponse(
        itemId,
        equipmentId,
        "モーター温度",
        InspectionItemType.NUMERIC,
        "℃",
        new BigDecimal("0"),
        new BigDecimal("80"),
        null,
        "モーターの温度を点検",
        1,
        true,
        LocalDateTime.of(2026, 1, 1, 10, 0),
        LocalDateTime.of(2026, 1, 2, 10, 0));
  }
}
