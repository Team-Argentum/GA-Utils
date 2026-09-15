package dev.team_argentum.ga_utils.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Struct {

    Backend backend() default Backend.UNSET;

    enum Backend {
        UNSET,
        FLATTEN,
        SOA,
        SSA
    }
}
