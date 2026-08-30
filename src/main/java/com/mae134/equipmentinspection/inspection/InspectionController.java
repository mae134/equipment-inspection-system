package com.mae134.equipmentinspection.inspection;

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
@RequestMapping("/api/inspections")
@Tag(name = "点検管理", description = "設備の点検情報を管理するAPI")
public class InspectionController {

  private final InspectionService inspectionService;

  public InspectionController(InspectionService inspectionService) {
    this.inspectionService = inspectionService;
  }

  @GetMapping
  @Operation(summary = "点検一覧を取得する")
  @ApiResponse(responseCode = "200", description = "点検一覧の取得に成功")
  public List<InspectionResponse> findAll() {
    return inspectionService.findAll();
  }

  @GetMapping("/{id}")
  @Operation(summary = "点検を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検の取得に成功"),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public InspectionResponse findById(@PathVariable Long id) {
    return inspectionService.findById(id);
  }

  @PostMapping
  @Operation(summary = "点検を登録する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検の登録に成功"),
    @ApiResponse(
        responseCode = "400",
        description = "入力値が不正",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "指定した設備またはユーザーが存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public InspectionResponse save(@Valid @RequestBody InspectionRequest request) {
    return inspectionService.save(request);
  }

  @PutMapping("/{id}")
  @Operation(summary = "点検を更新する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検の更新に成功"),
    @ApiResponse(
        responseCode = "400",
        description = "入力値が不正",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検、設備またはユーザーが存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public InspectionResponse update(
      @PathVariable Long id, @Valid @RequestBody InspectionRequest request) {
    return inspectionService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "点検を削除する")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "点検の削除に成功"),
    @ApiResponse(
        responseCode = "404",
        description = "指定した点検が存在しない",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public void delete(@PathVariable Long id) {
    inspectionService.delete(id);
  }
}
