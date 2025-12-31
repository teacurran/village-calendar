package villagecompute.calendar.services.jobs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DelayedJobHandler and configures its job properties. Handlers annotated with this are automatically
 * discovered and registered.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
public @interface DelayedJobConfig {

    /** Priority for job execution. Higher values execute first. Default is 5 (normal priority). */
    int priority() default 5;

    /** Human-readable description for logging and monitoring. */
    String description() default "";
}
