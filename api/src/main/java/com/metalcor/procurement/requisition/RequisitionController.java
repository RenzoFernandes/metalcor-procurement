package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requisitions")
@Tag(name = "Requisitions")
public class RequisitionController {

    private static final String USER_HEADER_DESCRIPTION =
            "Id of the acting user (app_users.id), must be active. Provisional auth until login exists.";

    private final RequisitionService service;

    public RequisitionController(RequisitionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a purchase requisition in draft status",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public ResponseEntity<RequisitionResponse> create(@Valid @RequestBody CreateRequisitionRequest request) {
        RequisitionResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/requisitions/" + response.id())).body(response);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft requisition for approval",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public RequisitionResponse submit(@PathVariable long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/decide")
    @Operation(summary = "Approve or reject a requisition pending approval",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public RequisitionResponse decide(@PathVariable long id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(id, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a purchase requisition by id")
    public RequisitionResponse get(@PathVariable long id) {
        return service.get(id);
    }
}