-- V11__requisition_closed_status.sql
-- Metalcor Procurement v0.4b: allow purchase_requisitions.status = 'closed'.
-- Requires V4 (purchase_requisitions). Fictional data only. Not SAP.
--
-- A requisition becomes closed once purchase orders have been issued for all its items
-- (see POST /api/v1/requisitions/{id}/issue-order). No table is created and no privileges
-- change, so there is nothing new to grant to metalcor_app or metalcor_readonly.

ALTER TABLE purchase_requisitions DROP CONSTRAINT ck_purchase_requisitions_status;
ALTER TABLE purchase_requisitions ADD CONSTRAINT ck_purchase_requisitions_status
    CHECK (status IN ('draft', 'pending_approval', 'approved', 'rejected', 'cancelled', 'closed'));

COMMENT ON COLUMN purchase_requisitions.status IS
    'draft, pending_approval, approved, rejected, cancelled or closed (after purchase orders were issued for all items).';
