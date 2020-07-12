package minegame159.meteorclient.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import minegame159.meteorclient.MeteorClient;
import minegame159.meteorclient.modules.ModuleManager;
import minegame159.meteorclient.modules.misc.EChestPreview;
import minegame159.meteorclient.modules.player.MountBypass;
import minegame159.meteorclient.utils.EChestMemory;
import minegame159.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ContainerProvider;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.container.Container;
import net.minecraft.container.Slot;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin<T extends Container> extends Screen implements ContainerProvider<T> {
    @Shadow protected Slot focusedSlot;

    @Shadow protected int x;
    @Shadow protected int y;
    private static final Identifier TEXTURE = new Identifier("meteor-client", "container_3x9.png");
    private static MinecraftClient mc;

    public ContainerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        mc = MinecraftClient.getInstance();

        // Dooop
        if (mc.player.getVehicle() instanceof AbstractDonkeyEntity) {
            AbstractDonkeyEntity entity = (AbstractDonkeyEntity) mc.player.getVehicle();

            addButton(new ButtonWidget(x + 82, y + 2, 39, 12, "Dupe", button -> {
                ModuleManager.INSTANCE.get(MountBypass.class).dontCancel();

                mc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, entity.getPos().add(entity.getWidth() / 2, entity.getHeight() / 2, entity.getWidth() / 2)));
            }));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (focusedSlot != null && !focusedSlot.getStack().isEmpty()) {
            // Shulker Preview
            if (Utils.isShulker(focusedSlot.getStack().getItem()) && MeteorClient.INSTANCE.shulkerPeek.isPressed()) {
                CompoundTag compoundTag = focusedSlot.getStack().getSubTag("BlockEntityTag");
                if (compoundTag != null) {
                    if (compoundTag.contains("Items", 9)) {
                        DefaultedList<ItemStack> itemStacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
                        Inventories.fromTag(compoundTag, itemStacks);

                        draw(itemStacks, mouseX, mouseY);
                    }
                }
            }

            // EChest preview
            if (focusedSlot.getStack().getItem() == Items.ENDER_CHEST && ModuleManager.INSTANCE.isActive(EChestPreview.class)) {
                draw(EChestMemory.ITEMS, mouseX, mouseY);
            }
        }
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void onDrawMouseoverTooltip(int mouseX, int mouseY, CallbackInfo info) {
        if (focusedSlot != null && !focusedSlot.getStack().isEmpty()) {
            if (Utils.isShulker(focusedSlot.getStack().getItem()) && MeteorClient.INSTANCE.shulkerPeek.isPressed()) info.cancel();
            else if (focusedSlot.getStack().getItem() == Items.ENDER_CHEST && ModuleManager.INSTANCE.isActive(EChestPreview.class)) info.cancel();
        }
    }

    private void draw(DefaultedList<ItemStack> itemStacks, int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        drawBackground(mouseX + 6, mouseY + 6);
        DiffuseLighting.enableForItems();

        int row = 0;
        int i = 0;
        for (ItemStack itemStack : itemStacks) {
            drawItem(itemStack, mouseX + 6 + 8 + i * 18, mouseY + 6 + 7 + row * 18);

            i++;
            if (i >= 9) {
                i = 0;
                row++;
            }
        }

        GlStateManager.enableLighting();
        GlStateManager.enableDepthTest();
    }

    private void drawItem(ItemStack itemStack, int x, int y) {
        mc.getItemRenderer().renderGuiItem(mc.player, itemStack, x, y);
        mc.getItemRenderer().renderGuiItemOverlay(mc.textRenderer, itemStack, x, y, null);
    }

    private void drawBackground(int x, int y) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        int width = 176;
        int height = 67;
        DrawableHelper.blit(x, y, 0, 0, 0, width, height, height, width);
    }

}
