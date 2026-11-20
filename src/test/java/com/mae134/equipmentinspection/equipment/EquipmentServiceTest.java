package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

  @Mock private EquipmentRepository equipmentRepository;

  private EquipmentService equipmentService;

  @BeforeEach
  void setUp() {
    equipmentService = new EquipmentService(equipmentRepository);
  }

  @Test
  void saveShouldReturnCreatedEquipment() {
    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-001",
            "ポンプA",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "定期点検対象設備",
            true);

    Equipment savedEquipment = new Equipment();
    savedEquipment.setId(1L);
    savedEquipment.setEquipmentCode("EQ-001");
    savedEquipment.setName("ポンプA");
    savedEquipment.setManufacturer("ABC");
    savedEquipment.setModel("P-100");
    savedEquipment.setLocation("工場A");
    savedEquipment.setInstalledDate(LocalDate.of(2026, 8, 6));
    savedEquipment.setInspectionCycleDays(30);
    savedEquipment.setDescription("定期点検対象設備");
    savedEquipment.setActive(true);
    savedEquipment.setCreatedAt(LocalDateTime.of(2026, 8, 6, 10, 0));
    savedEquipment.setUpdatedAt(LocalDateTime.of(2026, 8, 6, 10, 0));

    when(equipmentRepository.existsByEquipmentCode("EQ-001")).thenReturn(false);
    when(equipmentRepository.save(any(Equipment.class))).thenReturn(savedEquipment);

    EquipmentResponse response = equipmentService.save(request);

    assertEquals(1L, response.id());
    assertEquals("EQ-001", response.equipmentCode());
    assertEquals("ポンプA", response.name());

    verify(equipmentRepository).existsByEquipmentCode("EQ-001");
    verify(equipmentRepository).save(any(Equipment.class));
  }

  @Test
  void saveShouldThrowExceptionWhenEquipmentCodeAlreadyExists() {
    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-001",
            "ポンプA",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "定期点検対象設備",
            true);

    when(equipmentRepository.existsByEquipmentCode("EQ-001")).thenReturn(true);

    DuplicateResourceException exception =
        assertThrows(DuplicateResourceException.class, () -> equipmentService.save(request));

    assertEquals("設備コードは既に登録されています: EQ-001", exception.getMessage());

    verify(equipmentRepository).existsByEquipmentCode("EQ-001");
    verify(equipmentRepository, never()).save(any(Equipment.class));
  }

  @Test
  void searchShouldReturnAllEquipmentWhenKeywordIsNull() {
    Equipment equipment = new Equipment();
    equipment.setId(1L);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("ポンプA");

    when(equipmentRepository.findAll()).thenReturn(List.of(equipment));

    List<EquipmentResponse> result = equipmentService.search(null);

    assertEquals(1, result.size());
    assertEquals("EQ-001", result.get(0).equipmentCode());

    verify(equipmentRepository).findAll();
  }

  @Test
  void searchShouldReturnAllEquipmentWhenKeywordIsBlank() {
    Equipment equipment = new Equipment();
    equipment.setId(1L);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("ポンプA");

    when(equipmentRepository.findAll()).thenReturn(List.of(equipment));

    List<EquipmentResponse> result = equipmentService.search("   ");

    assertEquals(1, result.size());
    assertEquals("EQ-001", result.get(0).equipmentCode());

    verify(equipmentRepository).findAll();
  }

  @Test
  void searchShouldReturnMatchingEquipmentWhenKeywordIsSpecified() {
    Equipment equipment = new Equipment();
    equipment.setId(1L);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("ポンプA");

    when(equipmentRepository.findByEquipmentCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            "ポンプ", "ポンプ"))
        .thenReturn(List.of(equipment));

    List<EquipmentResponse> result = equipmentService.search("ポンプ");

    assertEquals(1, result.size());
    assertEquals("EQ-001", result.get(0).equipmentCode());
    assertEquals("ポンプA", result.get(0).name());

    verify(equipmentRepository)
        .findByEquipmentCodeContainingIgnoreCaseOrNameContainingIgnoreCase("ポンプ", "ポンプ");
  }

  @Test
  void updateShouldUpdateEquipmentAndPreserveInactiveStatus() {
    Long equipmentId = 1L;

    Equipment equipment = new Equipment();
    equipment.setId(equipmentId);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("更新前の設備");
    equipment.setActive(false);

    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-002",
            "更新後の設備",
            "新メーカー",
            "MODEL-002",
            "第二工場",
            LocalDate.of(2026, 11, 20),
            60,
            "更新後の説明",
            false);

    when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(equipmentRepository.existsByEquipmentCodeAndIdNot("EQ-002", equipmentId))
        .thenReturn(false);

    when(equipmentRepository.save(any(Equipment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EquipmentResponse response = equipmentService.update(equipmentId, request);

    ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
    verify(equipmentRepository).save(captor.capture());

    Equipment savedEquipment = captor.getValue();

    assertEquals("EQ-002", savedEquipment.getEquipmentCode());
    assertEquals("更新後の設備", savedEquipment.getName());
    assertEquals("新メーカー", savedEquipment.getManufacturer());
    assertEquals("MODEL-002", savedEquipment.getModel());
    assertEquals("第二工場", savedEquipment.getLocation());
    assertEquals(LocalDate.of(2026, 11, 20), savedEquipment.getInstalledDate());
    assertEquals(60, savedEquipment.getInspectionCycleDays());
    assertEquals("更新後の説明", savedEquipment.getDescription());
    assertEquals(false, savedEquipment.getActive());

    assertEquals(equipmentId, response.id());
    assertEquals("EQ-002", response.equipmentCode());
    assertEquals("更新後の設備", response.name());
    assertEquals(false, response.active());

    verify(equipmentRepository).findById(equipmentId);
    verify(equipmentRepository).existsByEquipmentCodeAndIdNot("EQ-002", equipmentId);
  }

  @Test
  void updateShouldAllowSameEquipmentCode() {
    Long equipmentId = 1L;

    Equipment equipment = new Equipment();
    equipment.setId(equipmentId);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("更新前の設備");
    equipment.setActive(true);

    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-001",
            "更新後の設備",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "設備名のみ変更",
            true);

    when(equipmentRepository.findById(equipmentId)).thenReturn(java.util.Optional.of(equipment));

    when(equipmentRepository.save(any(Equipment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EquipmentResponse response = equipmentService.update(equipmentId, request);

    assertEquals("EQ-001", response.equipmentCode());
    assertEquals("更新後の設備", response.name());

    verify(equipmentRepository).existsByEquipmentCodeAndIdNot("EQ-001", equipmentId);

    verify(equipmentRepository).save(any(Equipment.class));
  }

  @Test
  void updateShouldThrowExceptionWhenEquipmentCodeAlreadyExists() {
    Long equipmentId = 1L;

    Equipment equipment = new Equipment();
    equipment.setId(equipmentId);
    equipment.setEquipmentCode("EQ-001");
    equipment.setName("更新前の設備");

    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-002",
            "更新後の設備",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "設備コード重複テスト",
            true);

    when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(equipment));

    when(equipmentRepository.existsByEquipmentCodeAndIdNot("EQ-002", equipmentId)).thenReturn(true);

    DuplicateResourceException exception =
        assertThrows(
            DuplicateResourceException.class, () -> equipmentService.update(equipmentId, request));

    assertEquals("設備コードは既に登録されています: EQ-002", exception.getMessage());

    verify(equipmentRepository).findById(equipmentId);

    verify(equipmentRepository).existsByEquipmentCodeAndIdNot("EQ-002", equipmentId);

    verify(equipmentRepository, never()).save(any(Equipment.class));
  }

  @Test
  void updateShouldThrowNotFoundWhenEquipmentDoesNotExist() {
    Long equipmentId = 999L;

    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-999",
            "存在しない設備",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "存在しない設備の更新テスト",
            true);

    when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> equipmentService.update(equipmentId, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    verify(equipmentRepository).findById(equipmentId);

    verify(equipmentRepository, never()).existsByEquipmentCodeAndIdNot(any(), any());

    verify(equipmentRepository, never()).save(any(Equipment.class));
  }
}
