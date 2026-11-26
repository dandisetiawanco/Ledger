package com.ledger.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currencies")
public class Currency {

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Column(name = "minor_units", nullable = false)
    private int minorUnits;

    protected Currency() {
    }

    public Currency(String code, String name, String symbol, int minorUnits) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.minorUnits = minorUnits;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getMinorUnits() {
        return minorUnits;
    }
}
