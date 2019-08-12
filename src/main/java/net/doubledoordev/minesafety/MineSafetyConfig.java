package net.doubledoordev.minesafety;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class MineSafetyConfig
{
    public static final MineSafetyConfig.General GENERAL;
    static final ForgeConfigSpec spec;

    static
    {
        final Pair<General, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(MineSafetyConfig.General::new);
        spec = specPair.getRight();
        GENERAL = specPair.getLeft();
    }

    public static class General
    {
        public static List<? extends Integer> dimBlacklistList()
        {
            return new ArrayList<>();
        }

        public IntValue yLevel;
        public IntValue timeout;
        public DoubleValue chance;
        public ConfigValue<List<? extends Integer>> dimBlacklist;
        public ConfigValue<String> message;

        General(ForgeConfigSpec.Builder builder)
        {
            builder.comment("General configuration settings")
                    .push("General");

            yLevel = builder
                    .comment("The Y level at which you should wear a helmet.")
                    .translation("minesafety.config.yLevel")
                    .defineInRange("yLevel", 50, 0, 256);

            timeout = builder
                    .comment("The minimum time in seconds between 2 hits from this mod.")
                    .translation("minesafety.config.timeout")
                    .defineInRange("timeout", 1, 0, Integer.MAX_VALUE);

            chance = builder
                    .comment("The chance you get damaged this tick, in percent.")
                    .translation("minesafety.config.chance")
                    .defineInRange("chance", 0.03, 0, 1);

            dimBlacklist = builder
                    .comment("Dimension damage BLACKLIST, MineSafety damage will be disabled in these dimensions only!")
                    .translation("minesafety.config.dimlist")
                    .defineList("dimBlacklist", MineSafetyConfig.General.dimBlacklistList(), p -> p instanceof Integer);

            message = builder
                    .comment("The message displayed in-game when user takes damage from no helmet.")
                    .translation("minesafety.config.message")
                    .define("message", "Ouch! Falling rocks... I should wear a helmet.");

        }
    }
}
