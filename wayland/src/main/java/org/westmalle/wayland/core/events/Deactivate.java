package org.westmalle.wayland.core.events;

import com.google.auto.value.AutoValue;

@AutoValue
public class Deactivate {
    public static Deactivate create() {
        return new AutoValue_Deactivate();
    }
}
