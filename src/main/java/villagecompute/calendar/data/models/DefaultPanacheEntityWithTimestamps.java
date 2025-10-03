package villagecompute.calendar.data.models;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class DefaultPanacheEntityWithTimestamps extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    public Instant created;

    @UpdateTimestamp
    public Instant updated;

    @Version
    public Long version;

    public String toString() {
        String var10000 = this.getClass().getSimpleName();
        return var10000 + "<" + this.id + ">";
    }
}
