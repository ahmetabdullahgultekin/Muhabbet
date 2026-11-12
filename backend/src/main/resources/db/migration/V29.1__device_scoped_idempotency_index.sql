-- #750 (BE-07, ADR 0013 §3) — the uniqueness half of V29, split out per data-model.md §3.2 / rule 6.
--
-- While `client_message_id = id` the primary key already implies this index. It is kept because the
-- device-scoped pair is the idempotency key the protocol names (ICD §3.6), and OI-10 may yet move
-- message identity to that pair with a server-minted id — at which point this is the only guard.
-- Partial: legacy rows have no device and are matched by sender instead. CONCURRENTLY for the same
-- reason as V28.1, and alone in its file for the same reason.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_messages_sender_device_client_id
    ON messages (sender_device_id, client_message_id)
    WHERE sender_device_id IS NOT NULL;
