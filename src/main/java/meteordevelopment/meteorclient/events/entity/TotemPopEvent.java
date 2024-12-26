package meteordevelopment.meteorclient.events.entity;

import net.minecraft.entity.Entity;

public class TotemPopEvent {
    private static final TotemPopEvent INSTANCE = new TotemPopEvent();

    private Entity entity;
    private int pop;

    public static TotemPopEvent get(Entity entity, int pop) {
        INSTANCE.entity = entity;
        INSTANCE.pop = pop;
        return INSTANCE;
    }

    public Entity getEntity() {
        return entity;
    }

    public int getPop() {
        return pop;        
    }
}
