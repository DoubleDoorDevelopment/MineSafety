package net.doubledoordev.minesafety;

import java.util.ArrayList;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.network.FMLNetworkConstants;

@Mod("minesafety")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class MineSafety
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ItemDepthGauge depthGauge = new ItemDepthGauge(new Item.Properties().tab(ItemGroup.TAB_TOOLS));

    @SubscribeEvent
    public static void onRegisterItem(final RegistryEvent.Register<Item> event)
    {
        if (!MineSafetyConfig.GENERAL.serverSideOnly.get())
            event.getRegistry().register(depthGauge.setRegistryName("depthgauge"));
    }

    private final Random random = new Random();
    private final DamageSource UNSAFE_MINE = new DamageSource("mineSafetyUnsafeY").setScalesWithDifficulty();
    private final String NBTKey = "minesafetyCooldown";
    int tickCounterToStopLogSpam = 100;

    public MineSafety()
    {
        MinecraftForge.EVENT_BUS.register(MineSafetyConfig.class);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MineSafetyConfig.spec);

        MinecraftForge.EVENT_BUS.register(this);

        if (MineSafetyConfig.GENERAL.serverSideOnly.get())
        {
            ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void drawTextEvent(RenderGameOverlayEvent.Text event)
    {
        Minecraft mc = Minecraft.getInstance();
        ArrayList<String> list = event.getLeft();
        int yPos = mc.player.blockPosition().getY();

        // if we find a depth gauge
        if (mc.player.inventory.contains(new ItemStack(depthGauge)))
        {
            // render the pos, Colored if below the danger level.
            if (yPos <= MineSafetyConfig.GENERAL.yLevel.get())
            {
                list.add("\u00A74Y=" + yPos);
            }
            else
            {
                list.add("Y=" + yPos);
            }
            }
        }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event)
    {
        // Event fires two times per side, need to filter this.
        if (event.phase == TickEvent.Phase.END || event.side.isClient()) return;
        // Now we see if we should even hit the chance to damage.
        if (random.nextFloat() < MineSafetyConfig.GENERAL.chance.get()) return;

        PlayerEntity player = event.player;
        CompoundNBT data = player.getPersistentData();
        int coolDown = data.getInt(NBTKey);
        String dimResourceLocation = player.level.dimension().location().toString();

        // if the player has a helmet on we don't care. MUST BE REAL ARMOR!
        if (player.getItemBySlot(EquipmentSlotType.HEAD).getItem() instanceof ArmorItem) return;

        // Does the play hold a timeout value?
        if (!data.contains(NBTKey))
        {
            // If not, give them a new one with full time.
            data.putInt(NBTKey, 20 * MineSafetyConfig.GENERAL.timeout.get());
        }

        // This only exists to keep the log spam down.
        if (MineSafetyConfig.GENERAL.debug.get() && tickCounterToStopLogSpam > 100)
        {
            LOGGER.info(player.getDisplayName() + " is in Dim: [" + dimResourceLocation + "] To disable set debug to false in the config. Copy paste the text inside the [] to the dimlist to effect/block this dim.");
            tickCounterToStopLogSpam = 0;
        }

        // Is the player below the Y level, can't see they sky and the cooldown expired?
        if (player.getY() <= MineSafetyConfig.GENERAL.yLevel.get() &&
                !player.level.canSeeSky(new BlockPos(player.getX(), player.getY(), player.getZ())) &&
                coolDown == 0)
        {
            //check if the blacklist contains our current dim.
            if (!MineSafetyConfig.GENERAL.dimBlacklist.get().contains(dimResourceLocation))
            {
                //if not, damage them.
                damagePlayerAndNotify(data, player);
            }
        }
        else
        {
            // if we fail the Y, Sky or coolDown we make sure that it's a sane value.
            if (coolDown < 0)
                // If this happens some cheeky bastard was messing with our data!
                data.putInt(NBTKey, 0);
            else
                // otherwise we just take one off to work it down.
                data.putInt(NBTKey, coolDown - 1);
        }
    }

    private void damagePlayerAndNotify(CompoundNBT compoundNBT, PlayerEntity player)
    {
        // Damage is a magical beast that has some rules, We need to make sure we apply the damage!
        if (player.hurt(UNSAFE_MINE, 1.0f + 0.2f * random.nextFloat()))
        {
            // if the damage is applied then we send the message along with a handy timeout reset.
            player.displayClientMessage(new TranslationTextComponent(MineSafetyConfig.GENERAL.message.get()) {}, true);
            compoundNBT.putInt(NBTKey, 20 * MineSafetyConfig.GENERAL.timeout.get());
        }
    }
}

