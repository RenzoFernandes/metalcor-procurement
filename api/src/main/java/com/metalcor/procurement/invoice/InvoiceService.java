package com.metalcor.procurement.invoice;

import com.metalcor.procurement.common.BadRequestException;
import com.metalcor.procurement.common.ConflictException;
import com.metalcor.procurement.common.ForbiddenException;
import com.metalcor.procurement.common.NotFoundException;
import com.metalcor.procurement.invoice.InvoiceRepository.LineMatch;
import com.metalcor.procurement.invoice.InvoiceRepository.OrderInfo;
import com.metalcor.procurement.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private static final Set<String> INVOICEABLE_STATUSES = Set.of("received", "partially_received");

    private final InvoiceRepository invoices;
    private final JdbcClient jdbc;
    private final CurrentUser currentUser;

    public InvoiceService(InvoiceRepository invoices, JdbcClient jdbc, CurrentUser currentUser) {
        this.invoices = invoices;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public InvoiceResponse createInvoice(long orderId, InvoiceRequest request) {
        setAuditUser();

        OrderInfo order = invoices.findOrderInfo(orderId)
                .orElseThrow(() -> new NotFoundException("Purchase order " + orderId + " does not exist."));
        if (!INVOICEABLE_STATUSES.contains(order.status())) {
            throw new ConflictException("Purchase order " + orderId + " is " + order.status()
                    + ", expected received or partially_received.");
        }
        if (!"finance".equals(currentUser.role())) {
            throw new ForbiddenException("Role finance is required to register invoices.");
        }

        if (request.dueDate() != null && request.dueDate().isBefore(request.invoiceDate())) {
            throw new BadRequestException("dueDate must not be before invoiceDate.");
        }

        Set<Long> orderItemIds = invoices.findOrderItemIds(orderId);
        List<String> errors = new ArrayList<>();
        for (InvoiceItemRequest item : request.items()) {
            if (!orderItemIds.contains(item.purchaseOrderItemId())) {
                errors.add("purchaseOrderItemId " + item.purchaseOrderItemId()
                        + " does not belong to purchase order " + orderId + ".");
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }

        LocalDate dueDate = request.dueDate() != null
                ? request.dueDate()
                : request.invoiceDate().plusDays(order.paymentTermsDays());

        BigDecimal grossAmount = request.items().stream()
                .map(item -> item.quantityInvoiced().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long invoiceId;
        try {
            invoiceId = invoices.insertHeader(order.supplierId(), orderId, request.supplierInvoiceNumber(),
                    request.invoiceDate(), dueDate, grossAmount, "received", null);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("Supplier invoice number " + request.supplierInvoiceNumber()
                    + " was already registered for this supplier.");
        }

        int lineNumber = 1;
        for (InvoiceItemRequest item : request.items()) {
            invoices.insertItem(invoiceId, lineNumber++, item.purchaseOrderItemId(), item.quantityInvoiced(), item.unitPrice());
        }

        applyThreeWayMatch(invoiceId);

        return invoices.findById(invoiceId).orElseThrow();
    }

    public InvoiceResponse get(long id) {
        return invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice " + id + " does not exist."));
    }

    /** Reads the three-way match outcome just inserted (vw_invoice_line_match) and sets status/block_reason from it. */
    private void applyThreeWayMatch(long invoiceId) {
        List<LineMatch> lineMatches = invoices.findLineMatches(invoiceId);

        List<String> reasons = new ArrayList<>();
        lineMatches.stream()
                .filter(LineMatch::priceException)
                .findFirst()
                .ifPresent(m -> reasons.add("Preço da fatura acima do pedido (tolerância de "
                        + formatPct(m.priceTolerancePct()) + "%)"));
        lineMatches.stream()
                .filter(LineMatch::quantityException)
                .findFirst()
                .ifPresent(m -> reasons.add("Quantidade faturada maior que a recebida (tolerância de "
                        + formatPct(m.quantityTolerancePct()) + "%)"));

        String status = reasons.isEmpty() ? "matched" : "blocked";
        String blockReason = reasons.isEmpty() ? null : String.join("; ", reasons);
        invoices.updateStatus(invoiceId, status, blockReason);
    }

    private static String formatPct(BigDecimal pct) {
        return pct.stripTrailingZeros().toPlainString();
    }

    private void setAuditUser() {
        jdbc.sql("SELECT set_config('app.current_user_id', :userId, true)")
                .param("userId", String.valueOf(currentUser.id()))
                .query(String.class)
                .single();
    }
}