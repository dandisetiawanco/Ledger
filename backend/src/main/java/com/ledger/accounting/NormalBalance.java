package com.ledger.accounting;

import java.math.BigDecimal;

public enum NormalBalance {
    DEBIT,
    CREDIT;

    /**
     * Computes signed balance change according to the account's normal balance convention.
     */
    public BigDecimal netChange(BigDecimal debit, BigDecimal credit) {
        BigDecimal d = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal c = credit == null ? BigDecimal.ZERO : credit;
        return this == DEBIT ? d.subtract(c) : c.subtract(d);
    }
}
