package net.doubledoordev.minesafety;

import java.util.ArrayList;
import java.util.Random;

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
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.config.ModConfig;

@Mod("minesafety")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class MineSafety
{
    private Random random = new Random();
    private static ItemDepthGauge depthGauge = new ItemDepthGauge(new Item.Properties().group(ItemGroup.TOOLS));

    @SubscribeEvent
    public static void onRegisterItem(final RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(depthGauge.setRegistryName("depthgauge"));
    }

    private DamageSource UNSAFE_MINE = new DamageSource("mineSafetyUnsafeY").setDifficultyScaled();
    private String NBTKey = "minesafetyCooldown";

    public MineSafety()
    {
        MinecraftForge.EVENT_BUS.register(MineSafetyConfig.class);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MineSafetyConfig.spec);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void drawTextEvent(RenderGameOverlayEvent.Text event)
    {
        Minecraft mc = Minecraft.getInstance();
        ArrayList<String> list = event.getLeft();
        int yPos = mc.player.getPosition().getY();
        // loop over the player slots
        for(int i=0; i < 35; i++)
        {
            // if we find a depth gauge
            if (mc.player.inventory.getStackInSlot(i).isItemEqual(new ItemStack(depthGauge)))
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
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event)
    {
        // Event fires two times per side, need to filter this.
        if (event.phase == TickEvent.Phase.END || event.side.isClient()) return;
        // Now we see if we should even hit the chance to damage.
        if (random.nextFloat() < MineSafetyConfig.GENERAL.chance.get()) return;

        PlayerEntity player = event.player;
        CompoundNBT data = player.getEntityData();
        int coolDown = data.getInt(NBTKey);

        // if the player has a helmet on we don't care. MUST BE REAL ARMOR!
        if (player.getItemStackFromSlot(EquipmentSlotType.HEAD).getItem() instanceof ArmorItem) return;

        // Does the play hold a timeout value?
        if (!data.contains(NBTKey))
        {
            // If not, give them a new one with full time.
            data.putInt(NBTKey, 20 * MineSafetyConfig.GENERAL.timeout.get());
        }

        // Is the player below the Y level, can't see they sky and the cooldown expired?
        if (player.posY <= MineSafetyConfig.GENERAL.yLevel.get() &&
                !player.getEntityWorld().canBlockSeeSky(new BlockPos(player.posX, player.posY, player.posZ)) &&
                coolDown == 0)
        {
            // if we meet the above then we get into checking the blacklist.
            if (!MineSafetyConfig.GENERAL.dimBlacklist.get().isEmpty())
            {
                // if the blacklist isn't empty loop over all of the entries.
                for (int dim : MineSafetyConfig.GENERAL.dimBlacklist.get())
                {
                    // check the player dim against the blacklist entry
                    if (player.world.dimension.getType().getId() != dim)
                    {
                        // if they don't match damage them.
                        damagePlayerAndNotify(data, player);
                    }
                }
            }
            else
            {
                // if the blacklist is empty we don't care about any safe places, just give em a good whack.
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
        if (player.attackEntityFrom(UNSAFE_MINE, 1.0f + 0.2f * random.nextFloat()))
        {
            // if the damage is applied then we send the message along with a handy timeout reset.
            player.sendStatusMessage(new TranslationTextComponent(MineSafetyConfig.GENERAL.message.get()) {}, true);
            compoundNBT.putInt(NBTKey, 20 * MineSafetyConfig.GENERAL.timeout.get());
        }
    }
}

