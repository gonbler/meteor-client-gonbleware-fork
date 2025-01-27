package meteordevelopment.meteorclient.systems.modules.misc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.apache.commons.compress.utils.Sets;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.hit.BlockHitResult;

public class DebugModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<Class<? extends Packet<?>>>> c2sPackets =
            sgGeneral.add(new PacketListSetting.Builder().name("C2S-packets")
                    .description("Client-to-server packets to log.")
                    .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass)).build());

    private final Setting<Set<Class<? extends Packet<?>>>> s2cPackets =
            sgGeneral.add(new PacketListSetting.Builder().name("S2C-packets")
                    .description("Server-to-client packets to log.")
                    .filter(aClass -> PacketUtils.getS2CPackets().contains(aClass)).build());

    private static final Set<Class<?>> PRIMITIVE_TYPES =
            Sets.newHashSet(Boolean.class, Character.class, Byte.class, Short.class, Integer.class,
                    Long.class, Float.class, Double.class, Void.class, String.class);

    public DebugModule() {
        super(Categories.Misc, "debug-module",
                "A module for debugging. Don't touch this unless you know what you're doing.");
        runInMainMenu = true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.getClass())) {
            info(packetToString(event.packet));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSendPacket(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.getClass())) {
            info(packetToString(event.packet));
        }
    }

    private String packetToString(Packet<?> packet) {
        try {
            return reflectiveToString(packet);
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    private static String reflectiveToString(Object object) throws Exception {
        return reflectiveToString(object, 0);
    }

    // Code adapted from
    // https://stackoverflow.com/questions/3905382/recursively-print-an-objects-details

    // Adapted to print methods that start with get and shit instead of fields (making shit public
    // is hard)
    private static String reflectiveToString(Object object, int indentLevel) throws Exception {
        if (object == null) {
            return "null";
        }

        // Normal types like string int bool etc
        if (PRIMITIVE_TYPES.contains(object.getClass())) {
            if (object instanceof String) {
                return "\"" + object + "\"";
            }
            return object.toString();
        }

        // Enums can also be to-stringed easily
        if (object.getClass().isEnum()) {
            return object.toString();
        }

        if (object instanceof BlockHitResult bhr) {
            return "{ dir: " + bhr.getSide().toString() + ", bpos: "
                    + bhr.getBlockPos().toShortString() + ", pos: " + bhr.getPos().toString()
                    + ", inside: " + Boolean.toString(bhr.isInsideBlock()) + "}";
        }

        // Avoids stack overflow I guess
        if (!(object instanceof Packet<?>)) {
            return object.toString();
        }

        StringBuilder sb = new StringBuilder();

        String indent = "    ".repeat(indentLevel);
        sb.append(indent).append(object.getClass().getSimpleName()).append(" {\n");

        for (Method method : object.getClass().getMethods()) {
            String methodName = method.getName();

            // Don't do getPacketId because packets have a special method named that, and we don't
            // care about it
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class
                    && !Modifier.isStatic(method.getModifiers())) {
                Object value = method.invoke(object);

                sb.append(indent).append("    ").append(method.getReturnType().getSimpleName())
                        .append(" ").append(methodName).append(": ")
                        .append(reflectiveToString(value, indentLevel + 1)).append("\n");
            }
        }

        sb.append(indent).append("}");

        return sb.toString();
    }
}
