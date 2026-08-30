package com.mae134.equipmentinspection.inspectionitem;

import com.mae134.equipmentinspection.exception.ApiErrorResponse;
import com.mae134.equipmentinspection.exception.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-items")
@Tag(name = "点検項目管理", description = "設備ごとの点検項目を管理するAPI")
public class EquipmentInspectionItemController {

  private final EquipmentInspectionItemService inspectionItemService;

  public EquipmentInspectionItemController(EquipmentInspectionItemService inspectionItemService) {
    this.inspectionItemService = inspectionItemService;
  }

  @GetMapping
  @Operation(summary = "点検項目一覧を取得する")
  @ApiResponse(responseCode = "200", description = "点検項目一覧の取得に成功")
  public List<EquipmentInspectionItemResponse> findAll() {
    return inspectionItemService.findAll();
  }

  @GetMapping("/{id}")
  @Operation(summary = "点検項目を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検項目の取得に成功"),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検項目が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public EquipmentInspectionItemResponse findById(@PathVariable Long id) {
    return inspectionItemService.findById(id);
  }

  @PostMapping
  @Operation(summary = "点検項目を登録する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検項目の登録に成功"),
    @ApiResponse(
        responseCode = "400",
        description = "入力値または点検項目の設定が不正",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(oneOf = {ValidationErrorResponse.class, ApiErrorResponse.class}))),
    @ApiResponse(
        responseCode = "404",
        description = "指定した設備が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public EquipmentInspectionItemResponse save(
      @Valid @RequestBody EquipmentInspectionItemRequest request) {
    return inspectionItemService.save(request);
  }

  @PutMapping("/{id}")
  @Operation(summary = "点検項目を更新する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検項目の更新に成功"),
    @ApiResponse(
        responseCode = "400",
        description = "入力値または点検項目の設定が不正",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(oneOf = {ValidationErrorResponse.class, ApiErrorResponse.class}))),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検項目または設備が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public EquipmentInspectionItemResponse update(
      @PathVariable Long id, @Valid @RequestBody EquipmentInspectionItemRequest request) {
    return inspectionItemService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "点検項目を削除する")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "点検項目の削除に成功"),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検項目が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public void delete(@PathVariable Long id) {
    inspectionItemService.delete(id);
  }

  @GetMapping("/equipment/{equipmentId}")
  @Operation(summary = "設備に紐づく点検項目一覧を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "設備に紐づく点検項目一覧の取得に成功"),
    @ApiResponse(
        responseCode = "404",
        description = "指定した設備が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public List<EquipmentInspectionItemResponse> findByEquipmentId(@PathVariable Long equipmentId) {
    return inspectionItemService.findByEquipmentId(equipmentId);
  }
}
