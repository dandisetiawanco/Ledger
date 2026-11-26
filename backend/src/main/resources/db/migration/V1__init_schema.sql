-- Ledger Normalized Relational Schema
-- Compatible with PostgreSQL 16+ and H2 2.x (PostgreSQL compatibility mode)

CREATE TABLE currencies (
    code VARCHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    minor_units INTEGER NOT NULL CHECK (minor_units >= 0 AND minor_units <= 4)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_demo_user BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    business_type VARCHAR(100),
    base_currency VARCHAR(3) NOT NULL DEFAULT 'IDR' REFERENCES currencies(code),
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Jakarta',
    locale VARCHAR(20) NOT NULL DEFAULT 'id-ID',
    fiscal_year_start_month INTEGER NOT NULL DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    address TEXT,
    tax_id VARCHAR(100),
    logo_attachment_id UUID,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'ACCOUNTANT', 'FINANCE_OFFICER', 'APPROVER', 'VIEWER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    active_organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE financial_settings (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_prefix VARCHAR(20) NOT NULL DEFAULT 'INV',
    credit_note_prefix VARCHAR(20) NOT NULL DEFAULT 'CN',
    journal_prefix VARCHAR(20) NOT NULL DEFAULT 'JE',
    payment_prefix VARCHAR(20) NOT NULL DEFAULT 'PAY',
    expense_prefix VARCHAR(20) NOT NULL DEFAULT 'EXP',
    default_payment_terms_days INTEGER NOT NULL DEFAULT 30 CHECK (default_payment_terms_days >= 0),
    default_tax_name VARCHAR(60) NOT NULL DEFAULT 'VAT',
    default_tax_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000 CHECK (default_tax_rate >= 0),
    tax_inclusive_default BOOLEAN NOT NULL DEFAULT FALSE,
    require_expense_approval BOOLEAN NOT NULL DEFAULT TRUE,
    require_journal_approval BOOLEAN NOT NULL DEFAULT FALSE,
    require_invoice_approval BOOLEAN NOT NULL DEFAULT FALSE,
    enforce_separation_of_duties BOOLEAN NOT NULL DEFAULT TRUE,
    expense_approval_threshold NUMERIC(19, 4) NOT NULL DEFAULT 1000000.0000 CHECK (expense_approval_threshold >= 0),
    payment_instructions TEXT,
    invoice_footer_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    from_currency VARCHAR(3) NOT NULL REFERENCES currencies(code),
    to_currency VARCHAR(3) NOT NULL REFERENCES currencies(code),
    rate NUMERIC(19, 8) NOT NULL CHECK (rate > 0),
    effective_date DATE NOT NULL,
    source VARCHAR(64) NOT NULL DEFAULT 'MANUAL',
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_exchange_rate_org_pair_date UNIQUE (organization_id, from_currency, to_currency, effective_date)
);

CREATE TABLE fiscal_years (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fiscal_year_org_name UNIQUE (organization_id, name),
    CONSTRAINT chk_fiscal_year_dates CHECK (end_date >= start_date)
);

CREATE TABLE accounting_periods (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    fiscal_year_id UUID NOT NULL REFERENCES fiscal_years(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    period_number INTEGER NOT NULL CHECK (period_number >= 1),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID REFERENCES users(id),
    reopened_at TIMESTAMP WITH TIME ZONE,
    reopened_by UUID REFERENCES users(id),
    reopen_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_period_org_dates UNIQUE (organization_id, start_date, end_date),
    CONSTRAINT chk_period_dates CHECK (end_date >= start_date)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    account_type VARCHAR(24) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    normal_balance VARCHAR(8) NOT NULL CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    parent_account_id UUID REFERENCES accounts(id),
    system_code VARCHAR(40),
    cash_flow_category VARCHAR(24) CHECK (cash_flow_category IN ('CASH', 'OPERATING', 'INVESTING', 'FINANCING')),
    description TEXT,
    currency_code VARCHAR(3) REFERENCES currencies(code),
    allow_posting BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_account_org_code UNIQUE (organization_id, code)
);

CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    contact_type VARCHAR(16) NOT NULL CHECK (contact_type IN ('CUSTOMER', 'SUPPLIER', 'BOTH')),
    name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(200),
    email VARCHAR(255),
    phone VARCHAR(50),
    billing_address TEXT,
    tax_id VARCHAR(100),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    payment_terms_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_terms_days >= 0),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE catalog_items (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    sku VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    unit_price NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    revenue_account_id UUID NOT NULL REFERENCES accounts(id),
    default_tax_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000 CHECK (default_tax_rate >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_catalog_org_sku UNIQUE (organization_id, sku)
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entry_number VARCHAR(64) NOT NULL,
    entry_date DATE NOT NULL,
    accounting_period_id UUID NOT NULL REFERENCES accounting_periods(id),
    description VARCHAR(500) NOT NULL,
    reference VARCHAR(120),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'POSTED', 'REVERSED')),
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN ('MANUAL', 'INVOICE', 'CREDIT_NOTE', 'PAYMENT', 'EXPENSE', 'SUPPLIER_BILL', 'OPENING_BALANCE', 'REVERSAL', 'BANK_ADJUSTMENT')),
    source_id UUID,
    business_event_key VARCHAR(160),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    exchange_rate NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000 CHECK (exchange_rate > 0),
    exchange_rate_date DATE,
    total_debit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (total_debit >= 0),
    total_credit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (total_credit >= 0),
    reversed_entry_id UUID REFERENCES journal_entries(id),
    reversal_entry_id UUID REFERENCES journal_entries(id),
    created_by UUID REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    posted_by UUID REFERENCES users(id),
    posted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_journal_org_number UNIQUE (organization_id, entry_number),
    CONSTRAINT uq_journal_org_event_key UNIQUE (organization_id, business_event_key)
);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL CHECK (line_number >= 1),
    account_id UUID NOT NULL REFERENCES accounts(id),
    contact_id UUID REFERENCES contacts(id),
    description VARCHAR(500),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    original_debit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (original_debit >= 0),
    original_credit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (original_credit >= 0),
    debit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (debit_amount >= 0),
    credit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (credit_amount >= 0),
    CONSTRAINT chk_journal_line_one_side CHECK (NOT (debit_amount > 0 AND credit_amount > 0))
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_number VARCHAR(64) NOT NULL,
    customer_id UUID NOT NULL REFERENCES contacts(id),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    payment_terms_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_terms_days >= 0),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    exchange_rate NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000 CHECK (exchange_rate > 0),
    exchange_rate_date DATE,
    tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE,
    subtotal NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    discount_total NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    tax_total NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    base_total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    amount_paid NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (amount_paid >= 0),
    credit_applied NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (credit_applied >= 0),
    outstanding_balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (outstanding_balance >= 0),
    notes TEXT,
    payment_instructions TEXT,
    journal_entry_id UUID REFERENCES journal_entries(id),
    issued_at TIMESTAMP WITH TIME ZONE,
    voided_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoice_org_number UNIQUE (organization_id, invoice_number)
);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL CHECK (line_number >= 1),
    catalog_item_id UUID REFERENCES catalog_items(id),
    description VARCHAR(500) NOT NULL,
    revenue_account_id UUID NOT NULL REFERENCES accounts(id),
    quantity NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    discount_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000 CHECK (discount_rate >= 0 AND discount_rate <= 100),
    discount_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (discount_amount >= 0),
    tax_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_rate >= 0),
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_amount >= 0),
    net_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    line_total NUMERIC(19, 4) NOT NULL DEFAULT 0.0000
);

CREATE TABLE credit_notes (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    credit_note_number VARCHAR(64) NOT NULL,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    customer_id UUID NOT NULL REFERENCES contacts(id),
    credit_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    exchange_rate NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000 CHECK (exchange_rate > 0),
    net_amount NUMERIC(19, 4) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_amount >= 0),
    total_amount NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    base_total_amount NUMERIC(19, 4) NOT NULL CHECK (base_total_amount > 0),
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    journal_entry_id UUID REFERENCES journal_entries(id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_credit_note_org_number UNIQUE (organization_id, credit_note_number)
);

CREATE TABLE expense_categories (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    default_account_id UUID NOT NULL REFERENCES accounts(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_expense_category_org_name UNIQUE (organization_id, name)
);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    expense_number VARCHAR(64) NOT NULL,
    expense_type VARCHAR(24) NOT NULL CHECK (expense_type IN ('IMMEDIATE_EXPENSE', 'SUPPLIER_BILL')),
    title VARCHAR(250) NOT NULL,
    expense_date DATE NOT NULL,
    due_date DATE,
    supplier_id UUID REFERENCES contacts(id),
    category_id UUID REFERENCES expense_categories(id),
    expense_account_id UUID NOT NULL REFERENCES accounts(id),
    payment_account_id UUID REFERENCES accounts(id),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    exchange_rate NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000 CHECK (exchange_rate > 0),
    exchange_rate_date DATE,
    net_amount NUMERIC(19, 4) NOT NULL CHECK (net_amount > 0),
    tax_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_rate >= 0),
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_amount >= 0),
    total_amount NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    base_total_amount NUMERIC(19, 4) NOT NULL CHECK (base_total_amount > 0),
    amount_paid NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (amount_paid >= 0),
    outstanding_balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (outstanding_balance >= 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED', 'REVERSED')),
    notes TEXT,
    journal_entry_id UUID REFERENCES journal_entries(id),
    created_by UUID REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    posted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_expense_org_number UNIQUE (organization_id, expense_number)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    payment_number VARCHAR(64) NOT NULL,
    payment_type VARCHAR(24) NOT NULL CHECK (payment_type IN ('CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT')),
    contact_id UUID NOT NULL REFERENCES contacts(id),
    payment_date DATE NOT NULL,
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'MANUAL_EXTERNAL')),
    bank_account_id UUID NOT NULL REFERENCES accounts(id),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    exchange_rate NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000 CHECK (exchange_rate > 0),
    exchange_rate_date DATE,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    base_amount NUMERIC(19, 4) NOT NULL CHECK (base_amount > 0),
    allocated_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (allocated_amount >= 0),
    unallocated_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (unallocated_amount >= 0),
    reference_number VARCHAR(120),
    notes TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'REVERSED')),
    idempotency_key VARCHAR(128),
    journal_entry_id UUID REFERENCES journal_entries(id),
    reversal_journal_entry_id UUID REFERENCES journal_entries(id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_org_number UNIQUE (organization_id, payment_number),
    CONSTRAINT uq_payment_org_idempotency UNIQUE (organization_id, idempotency_key)
);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    invoice_id UUID REFERENCES invoices(id),
    expense_id UUID REFERENCES expenses(id),
    allocation_date DATE NOT NULL,
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount > 0),
    base_allocated_amount NUMERIC(19, 4) NOT NULL CHECK (base_allocated_amount > 0),
    realized_fx_gain_loss NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    institution_name VARCHAR(160) NOT NULL,
    masked_account_number VARCHAR(64),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    gl_account_id UUID NOT NULL REFERENCES accounts(id),
    opening_balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    opening_balance_date DATE,
    opening_journal_entry_id UUID REFERENCES journal_entries(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_bank_account_org_gl UNIQUE (organization_id, gl_account_id)
);

CREATE TABLE bank_statement_imports (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(128) NOT NULL,
    total_rows INTEGER NOT NULL CHECK (total_rows >= 0),
    imported_rows INTEGER NOT NULL CHECK (imported_rows >= 0),
    duplicate_rows INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_rows >= 0),
    imported_by UUID REFERENCES users(id),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE bank_statement_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id) ON DELETE CASCADE,
    statement_import_id UUID NOT NULL REFERENCES bank_statement_imports(id) ON DELETE CASCADE,
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    reference VARCHAR(160),
    amount NUMERIC(19, 4) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_bank_stmt_line_fingerprint UNIQUE (organization_id, bank_account_id, fingerprint)
);

CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id) ON DELETE CASCADE,
    bank_statement_line_id UUID NOT NULL REFERENCES bank_statement_lines(id),
    journal_line_id UUID NOT NULL REFERENCES journal_lines(id),
    active_bank_line_id UUID UNIQUE,
    active_journal_line_id UUID UNIQUE,
    matched_amount NUMERIC(19, 4) NOT NULL,
    match_type VARCHAR(24) NOT NULL CHECK (match_type IN ('EXACT', 'MANUAL', 'CREATED_JOURNAL')),
    status VARCHAR(16) NOT NULL DEFAULT 'MATCHED' CHECK (status IN ('MATCHED', 'UNMATCHED')),
    notes VARCHAR(500),
    reconciled_by UUID REFERENCES users(id),
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unmatched_by UUID REFERENCES users(id),
    unmatched_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    fiscal_year_id UUID REFERENCES fiscal_years(id),
    period_type VARCHAR(16) NOT NULL CHECK (period_type IN ('ANNUAL', 'MONTHLY')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    notes TEXT,
    revision_number INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_budget_dates CHECK (end_date >= start_date)
);

CREATE TABLE budget_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    period_month INTEGER CHECK (period_month IS NULL OR (period_month BETWEEN 1 AND 12)),
    planned_amount NUMERIC(19, 4) NOT NULL CHECK (planned_amount >= 0),
    notes VARCHAR(500),
    CONSTRAINT uq_budget_line_account_month UNIQUE (budget_id, account_id, period_month)
);

CREATE TABLE budget_revisions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    change_summary TEXT NOT NULL,
    revised_by UUID REFERENCES users(id),
    revised_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL CHECK (entity_type IN ('EXPENSE', 'SUPPLIER_BILL', 'MANUAL_JOURNAL', 'INVOICE')),
    entity_id UUID NOT NULL,
    entity_reference VARCHAR(120) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'RETURNED')),
    requester_id UUID NOT NULL REFERENCES users(id),
    approver_id UUID REFERENCES users(id),
    decision_reason TEXT,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL CHECK (entity_type IN ('EXPENSE', 'INVOICE', 'PAYMENT', 'ORGANIZATION_LOGO')),
    entity_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    sha256_hash VARCHAR(64) NOT NULL,
    uploaded_by UUID REFERENCES users(id),
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    category VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    entity_type VARCHAR(32),
    entity_id UUID,
    dedup_key VARCHAR(180),
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_org_dedup UNIQUE (organization_id, dedup_key)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_email VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(120),
    request_id VARCHAR(64),
    summary TEXT NOT NULL,
    metadata_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    resource_id UUID,
    response_status INTEGER NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_org_op_key UNIQUE (organization_id, operation, idempotency_key)
);

CREATE TABLE document_sequences (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    sequence_type VARCHAR(32) NOT NULL,
    prefix VARCHAR(24) NOT NULL,
    seq_year INTEGER NOT NULL,
    next_value BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_doc_sequence UNIQUE (organization_id, sequence_type, seq_year)
);

-- Indexes for accounting, reporting, and multi-tenant queries
CREATE INDEX idx_org_members_user ON organization_memberships (user_id);
CREATE INDEX idx_sessions_user ON user_sessions (user_id);
CREATE INDEX idx_periods_org_dates ON accounting_periods (organization_id, start_date, end_date);
CREATE INDEX idx_accounts_org_type ON accounts (organization_id, account_type, active);
CREATE INDEX idx_accounts_org_system ON accounts (organization_id, system_code);
CREATE INDEX idx_journals_org_date_status ON journal_entries (organization_id, entry_date, status);
CREATE INDEX idx_journals_org_source ON journal_entries (organization_id, source_type, source_id);
CREATE INDEX idx_jlines_org_account ON journal_lines (organization_id, account_id);
CREATE INDEX idx_jlines_entry ON journal_lines (journal_entry_id);
CREATE INDEX idx_contacts_org_type ON contacts (organization_id, contact_type, active);
CREATE INDEX idx_invoices_org_status_due ON invoices (organization_id, status, due_date);
CREATE INDEX idx_invoices_org_customer ON invoices (organization_id, customer_id);
CREATE INDEX idx_expenses_org_status_date ON expenses (organization_id, status, expense_date);
CREATE INDEX idx_expenses_org_supplier ON expenses (organization_id, supplier_id);
CREATE INDEX idx_payments_org_date ON payments (organization_id, payment_date);
CREATE INDEX idx_palloc_invoice ON payment_allocations (invoice_id, allocation_date);
CREATE INDEX idx_palloc_expense ON payment_allocations (expense_id, allocation_date);
CREATE INDEX idx_bank_stmt_lines_acct_date ON bank_statement_lines (bank_account_id, transaction_date, reconciled);
CREATE INDEX idx_reconcil_org_acct ON reconciliation_records (organization_id, bank_account_id, status);
CREATE INDEX idx_approvals_org_status ON approval_requests (organization_id, status);
CREATE INDEX idx_audit_org_occurred ON audit_events (organization_id, occurred_at DESC);
CREATE INDEX idx_notifications_org_created ON notifications (organization_id, created_at DESC);
