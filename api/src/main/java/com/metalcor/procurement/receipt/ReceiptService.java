package com.metalcor.procurement.receipt;

import com.metalcor.procurement.common.BadRequestException;
import com.metalcor.procurement.common.ConflictException;
import com.metalcor.procurement.common.NotFoundException;
import com.metalcor.procurement.receipt.ReceiptRepository.ItemProgress;
import com.metalcor.procurement.receipt.ReceiptRepository.OrderInfo;
import com.metalcor.procurement.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {

    private static final Set<String> RECEIVABLE_STATUSES = Set.of("issued", "partially_received");

    private final ReceiptRepository receipts;
    private final JdbcClient jdbc;
    private final CurrentUser currentUser;

    public ReceiptService(ReceiptRepository receipts, JdbcClient jdbc, CurrentUser currentUser) {
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public CreateReceiptResponse createReceipt(long orderId, ReceiptRequest request) {
        setAuditUser();

        OrderInfo order = receipts.findOrderStatusAndPlant(orderId)
                .orElseThrow(() -> new NotFoundException("Purchase order " + orderId + " does not exist."));
        if (!RECEIVABLE_STATUSES.contains(order.status())) {
            throw new ConflictException("Purchase order " + orderId + " is " + order.status()
                    + ", expected issued or partially_received.");
        }

        Set<Long> seen = new LinkedHashSet<>();
        for (ReceiptItemRequest item : request.items()) {
            if (!seen.add(item.purchaseOrderItemId())) {
                throw new BadRequestException("purchaseOrderItemId " + item.purchaseOrderItemId()
                        + " is duplicated in the request.");
            }
        }

        List<ItemProgress> progress = receipts.findOrderItemProgress(orderId);
        Map<Long, ItemProgress> progressById = new LinkedHashMap<>();
        for (ItemProgress p : progress) {
            progressById.put(p.purchaseOrderItemId(), p);
        }

        List<String> errors = new ArrayList<>();
        for (ReceiptItemRequest item : request.items()) {
            ItemProgress p = progressById.get(item.purchaseOrderItemId());
            if (p == null) {
                errors.add("purchaseOrderItemId " + item.purchaseOrderItemId()
                        + " does not belong to purchase order " + orderId + ".");
                continue;
            }
            BigDecimal remaining = p.orderedQuantity().subtract(p.receivedQuantity());
            if (item.quantityReceived().compareTo(remaining) > 0) {
                errors.add("purchaseOrderItemId " + item.purchaseOrderItemId() + " can receive at most " + remaining
                        + " more (ordered " + p.orderedQuantity() + ", already received " + p.receivedQuantity() + ").");
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }

        long receiptId = receipts.insertHeader(orderId, order.plantId(), currentUser.id(), request.deliveryNoteNumber());

        int lineNumber = 1;
        Map<Long, BigDecimal> addedByItem = new LinkedHashMap<>();
        for (ReceiptItemRequest item : request.items()) {
            receipts.insertItem(receiptId, lineNumber++, item.purchaseOrderItemId(), item.quantityReceived());
            addedByItem.put(item.purchaseOrderItemId(), item.quantityReceived());
        }

        boolean allComplete = true;
        boolean anyReceived = false;
        for (ItemProgress p : progress) {
            BigDecimal totalReceived = p.receivedQuantity().add(addedByItem.getOrDefault(p.purchaseOrderItemId(), BigDecimal.ZERO));
            if (totalReceived.compareTo(BigDecimal.ZERO) > 0) {
                anyReceived = true;
            }
            if (totalReceived.compareTo(p.orderedQuantity()) < 0) {
                allComplete = false;
            }
        }
        String newStatus = allComplete ? "received" : (anyReceived ? "partially_received" : "issued");
        receipts.updateOrderStatus(orderId, newStatus);

        GoodsReceiptResponse receipt = receipts.findById(receiptId).orElseThrow();
        return new CreateReceiptResponse(receipt, newStatus);
    }

    public GoodsReceiptResponse get(long id) {
        return receipts.findById(id)
                .orElseThrow(() -> new NotFoundException("Goods receipt " + id + " does not exist."));
    }

    private void setAuditUser() {
        jdbc.sql("SELECT set_config('app.current_user_id', :userId, true)")
                .param("userId", String.valueOf(currentUser.id()))
                .query(String.class)
                .single();
    }
}
