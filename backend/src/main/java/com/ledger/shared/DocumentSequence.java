package com.ledger.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "sequence_type", nullable = false, length = 32)
    private String sequenceType;

    @Column(nullable = false, length = 24)
    private String prefix;

    @Column(name = "seq_year", nullable = false)
    private int seqYear;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Version
    private long version;

    protected DocumentSequence() {
    }

    public DocumentSequence(UUID organizationId, String sequenceType, String prefix, int seqYear) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sequenceType = sequenceType;
        this.prefix = prefix;
        this.seqYear = seqYear;
        this.nextValue = 1L;
    }

    public long consumeNextValue() {
        long current = this.nextValue;
        this.nextValue = current + 1;
        return current;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getSeqYear() {
        return seqYear;
    }
}
