// package meteordevelopment.meteorclient.commands.commands;

// import com.mojang.brigadier.builder.LiteralArgumentBuilder;
// import com.mojang.brigadier.context.CommandContext;
// import meteordevelopment.meteorclient.commands.Command;
// import meteordevelopment.meteorclient.systems.modules.Modules;
// import meteordevelopment.meteorclient.systems.modules.movement.GrimBoatTeleport;
// import net.minecraft.command.CommandSource;
// import net.minecraft.command.argument.PosArgument;
// import net.minecraft.command.argument.Vec3ArgumentType;
// import net.minecraft.util.math.BlockPos;

// public class BoatFlyPathCommand extends Command {
//     public BoatFlyPathCommand() {
//         super("boatfly", "Boats your fly.");
//     }

//     @Override
//     public void build(LiteralArgumentBuilder<CommandSource> builder) {
//         builder.then(argument("pos", Vec3ArgumentType.vec3())
//                 .executes(context -> startPathfinding(context)));
//     }

//     private int startPathfinding(CommandContext<CommandSource> context) {
//         if (mc.player == null)
//             return -1;

//         if (!Modules.get().get(GrimBoatTeleport.class).isActive()) {
//             return -1;
//         }

//         BlockPos pos = context.getArgument("pos", PosArgument.class)
//                 .toAbsoluteBlockPos(mc.player.getCommandSource());

//         Modules.get().get(GrimBoatTeleport.class).pathTo(pos);

//         return SINGLE_SUCCESS;
//     }
// }
