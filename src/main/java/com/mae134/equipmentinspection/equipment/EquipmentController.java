package com.mae134.equipmentinspection.equipment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/equipment")
@Tag(name = "設備管理", description = "設備情報を管理するAPI")
public class EquipmentController {

  private final EquipmentService equipmentService;

  public EquipmentController(EquipmentService equipmentService) {
    this.equipmentService = equipmentService;
  }

  @GetMapping
  @Operation(summary = "設備一覧を取得する")
  @ApiResponse(responseCode = "200", description = "設備一覧の取得に成功")
  public List<EquipmentResponse> findAll() {
    return equipmentService.findAll();
  }

  @PostMapping
  @Operation(summary = "設備を登録する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "設備の登録に成功"),
    @ApiResponse(responseCode = "400", description = "入力値が不正"),
    @ApiResponse(responseCode = "409", description = "設備コードが重複している")
  })
  public EquipmentResponse save(@Valid @RequestBody EquipmentRequest request) {
    return equipmentService.save(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "設備を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "設備の取得に成功"),
    @ApiResponse(responseCode = "404", description = "指定した設備が存在しない")
  })
  public EquipmentResponse findById(@PathVariable Long id) {
    return equipmentService
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "設備を更新する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "設備の更新に成功"),
    @ApiResponse(responseCode = "400", description = "入力値が不正"),
    @ApiResponse(responseCode = "404", description = "指定した設備が存在しない"),
    @ApiResponse(responseCode = "409", description = "設備コードが重複している")
  })
  public EquipmentResponse update(
      @PathVariable Long id, @Valid @RequestBody EquipmentRequest request) {
    return equipmentService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "設備を削除する")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "設備の削除に成功"),
    @ApiResponse(responseCode = "404", description = "指定した設備が存在しない")
  })
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    equipmentService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
