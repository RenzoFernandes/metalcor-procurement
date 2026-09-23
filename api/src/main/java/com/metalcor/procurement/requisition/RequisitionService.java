package com.metalcor.procurement.requisition;

import com.metalcor.procurement.common.BadRequestException;
import com.metalcor.procurement.common.ConflictException;
import com.metalcor.procurement.common.ForbiddenException;
import com.metalcor.procurement.common.NotFoundException;
import com.metalcor.procurement.requisition.RequisitionRepository.ApprovalRule;
import com.metalcor.procurement.requisition.RequisitionRepository.StatusAndTotal;
import com.metalcor.procurement.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequisitionService {

    private final RequisitionRepository requisitions;
    private final JdbcClient jdbc;
    private final CurrentUser currentUser;

    public RequisitionService(RequisitionRepository requisitions, JdbcClient jdbc, CurrentUser currentUser) {
        this.requisitions = requisitions;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public RequisitionResponse create(CreateRequisitionRequest request) {
        setAuditUser();

        Set<Long> materialIds = request.items().stream()
                .map(CreateRequisitionItemRequest::materialId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> unitIds = request.items().stream()
                .map(CreateRequisitionItemRequest::unitOfMeasureId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> existingMaterials = requisitions.existingMaterialIds(materialIds);
        Set<Long> existingUnits = requisitions.existingUnitOfMeasureIds(unitIds);

        List<String> errors = new ArrayList<>();
        materialIds.stream()
                .filter(id -> !existingMaterials.contains(id))
                .forEach(id -> errors.add("materialId " + id + " does not exist"));
        unitIds.stream()
                .filter(id -> !existingUnits.contains(id))
                .forEach(id -> errors.add("unitOfMeasureId " + id + " does not exist"));
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }

        long id = requisitions.insertHeader(request.plantId(), request.costCenterId(), currentUser.id(),
                request.neededBy(), request.notes());

        int lineNumber = 1;
        for (CreateRequisitionItemRequest item : request.items()) {
            requisitions.insertItem(id, lineNumber++, item.materialId(), item.quantity(), item.unitOfMeasureId(),
                    item.estimatedUnitPrice(), item.suggestedSupplierId(), item.neededBy());
        }

        return requisitions.findById(id).orElseThrow();
    }

    @Transactional
    public RequisitionResponse submit(long id) {
        setAuditUser();
        String status = requisitions.findStatus(id)
                .orElseThrow(() -> new NotFoundException("Requisition " + id + " does not exist."));
        if (!"draft".equals(status)) {
            throw new ConflictException("Requisition " + id + " is " + status + ", expected draft.");
        }
        requisitions.submit(id);
        return requisitions.findById(id).orElseThrow();
    }

    @Transactional
    public RequisitionResponse decide(long id, DecisionRequest request) {
        setAuditUser();

        if ("reject".equals(request.decision()) && (request.comment() == null || request.comment().isBlank())) {
            throw new BadRequestException("comment is required when decision is reject.");
        }

        StatusAndTotal statusAndTotal = requisitions.findStatusAndTotal(id)
                .orElseThrow(() -> new NotFoundException("Requisition " + id + " does not exist."));
        if (!"pending_approval".equals(statusAndTotal.status())) {
            throw new ConflictException("Requisition " + id + " is " + statusAndTotal.status() + ", expected pending_approval.");
        }

        BigDecimal total = statusAndTotal.total();
        ApprovalRule rule = requisitions.findApprovalRule(total)
                .orElseThrow(() -> new ConflictException("No active approval rule covers amount " + total + "."));

        if (!rule.requiredRole().equals(currentUser.role())) {
            throw new ForbiddenException("Role " + rule.requiredRole() + " is required to decide this requisition.");
        }

        String decision = "approve".equals(request.decision()) ? "approved" : "rejected";
        requisitions.insertApproval(id, rule.requiredRole(), rule.id(), currentUser.id(), decision, request.comment(), total);

        if ("approved".equals(decision)) {
            requisitions.approve(id, currentUser.id());
        } else {
            requisitions.reject(id, request.comment());
        }

        return requisitions.findById(id).orElseThrow();
    }

    public RequisitionResponse get(long id) {
        return requisitions.findById(id)
                .orElseThrow(() -> new NotFoundException("Requisition " + id + " does not exist."));
    }

    private void setAuditUser() {
        jdbc.sql("SELECT set_config('app.current_user_id', :userId, true)")
                .param("userId", String.valueOf(currentUser.id()))
                .query(String.class)
                .single();
    }
}