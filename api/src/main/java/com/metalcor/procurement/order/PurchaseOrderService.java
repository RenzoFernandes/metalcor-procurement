package com.metalcor.procurement.order;

import com.metalcor.procurement.common.BadRequestException;
import com.metalcor.procurement.common.ConflictException;
import com.metalcor.procurement.common.ForbiddenException;
import com.metalcor.procurement.common.NotFoundException;
import com.metalcor.procurement.order.PurchaseOrderRepository.SupplierPrice;
import com.metalcor.procurement.requisition.RequisitionItemResponse;
import com.metalcor.procurement.requisition.RequisitionRepository;
import com.metalcor.procurement.requisition.RequisitionResponse;
import com.metalcor.procurement.security.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final RequisitionRepository requisitions;
    private final JdbcClient jdbc;
    private final CurrentUser currentUser;

    public PurchaseOrderService(PurchaseOrderRepository orders, RequisitionRepository requisitions,
            JdbcClient jdbc, CurrentUser currentUser) {
        this.orders = orders;
        this.requisitions = requisitions;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public IssueOrderResponse issueOrder(long requisitionId) {
        setAuditUser();

        RequisitionResponse requisition = requisitions.findById(requisitionId)
                .orElseThrow(() -> new NotFoundException("Requisition " + requisitionId + " does not exist."));
        if (!"approved".equals(requisition.status())) {
            throw new ConflictException("Requisition " + requisitionId + " is " + requisition.status() + ", expected approved.");
        }
        if (!"buyer".equals(currentUser.role())) {
            throw new ForbiddenException("Role buyer is required to issue purchase orders.");
        }

        List<Selection> selections = selectSuppliers(requisition.items());

        Map<Long, List<Selection>> bySupplier = selections.stream()
                .collect(Collectors.groupingBy(Selection::supplierId, LinkedHashMap::new, Collectors.toList()));

        List<PurchaseOrderResponse> created = new ArrayList<>();
        for (Map.Entry<Long, List<Selection>> entry : bySupplier.entrySet()) {
            created.add(issueOrderForSupplier(requisitionId, requisition.plant().id(), entry.getKey(), entry.getValue()));
        }

        requisitions.close(requisitionId);

        return new IssueOrderResponse(created, "closed");
    }

    public PurchaseOrderResponse get(long id) {
        return orders.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order " + id + " does not exist."));
    }

    /** Picks a supplier and today's price for each item: the suggested one if it has a current price, else the cheapest. */
    private List<Selection> selectSuppliers(List<RequisitionItemResponse> items) {
        List<Selection> selections = new ArrayList<>();
        List<String> materialsWithoutSupplier = new ArrayList<>();

        for (RequisitionItemResponse item : items) {
            long materialId = item.material().id();

            if (item.suggestedSupplier() != null) {
                Optional<SupplierPrice> price = orders.findCurrentPrice(item.suggestedSupplier().id(), materialId);
                if (price.isEmpty()) {
                    throw new BadRequestException("Suggested supplier " + item.suggestedSupplier().code()
                            + " has no current price for material " + item.material().code() + ".");
                }
                selections.add(new Selection(item, price.get()));
            } else {
                Optional<SupplierPrice> price = orders.findCheapestCurrentPrice(materialId);
                if (price.isEmpty()) {
                    materialsWithoutSupplier.add(item.material().code());
                } else {
                    selections.add(new Selection(item, price.get()));
                }
            }
        }

        if (!materialsWithoutSupplier.isEmpty()) {
            throw new BadRequestException("No supplier has a current price for materials: "
                    + String.join(", ", materialsWithoutSupplier));
        }
        return selections;
    }

    private PurchaseOrderResponse issueOrderForSupplier(long requisitionId, long plantId, long supplierId, List<Selection> items) {
        int paymentTermsDays = orders.findPaymentTermsDays(supplierId);
        int maxLeadTimeDays = items.stream().mapToInt(s -> s.price().leadTimeDays()).max().orElseThrow();

        long orderId = orders.insertHeader(supplierId, requisitionId, plantId, currentUser.id(), paymentTermsDays, maxLeadTimeDays);

        int lineNumber = 1;
        for (Selection selection : items) {
            RequisitionItemResponse item = selection.item();
            orders.insertItem(orderId, lineNumber++, item.material().id(), item.id(),
                    item.quantity(), item.unitOfMeasure().id(), selection.price().unitPrice());
        }

        return orders.findById(orderId).orElseThrow();
    }

    private void setAuditUser() {
        jdbc.sql("SELECT set_config('app.current_user_id', :userId, true)")
                .param("userId", String.valueOf(currentUser.id()))
                .query(String.class)
                .single();
    }

    private record Selection(RequisitionItemResponse item, SupplierPrice price) {
        long supplierId() {
            return price.supplierId();
        }
    }
}
