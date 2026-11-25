package vn.nhaminh.api.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import vn.nhaminh.api.family.Family;

@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpenseCategory() {}

    public ExpenseCategory(Family family, String name, TransactionType type) {
        this.family = family;
        this.name = name;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public String getName() { return name; }
    public TransactionType getType() { return type; }
}
