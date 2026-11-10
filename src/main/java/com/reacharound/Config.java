package com.reacharound;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    // Quark-style reach-around (aim at nothing next to an edge)
    public static boolean reachAroundEnabled = true;
    public static float leniency = 0.5F;
    public static boolean showIndicator = true;

    // Edge snapping (aim at the very edge of a face)
    public static boolean edgeSnap = false;
    public static float edgeMargin = 0.1F;
    public static boolean allowNearEdges = false;
    public static boolean affectSideFaces = false;
    public static boolean requireSneaking = false;

    private static final String QUARK = "quark_style";
    private static final String EDGE = "edge_snap";

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        reachAroundEnabled = configuration.getBoolean(
            "enabled",
            QUARK,
            reachAroundEnabled,
            "Quark-style reach-around: with a block in hand and the crosshair on nothing, you can still place a "
                + "block next to or under a block that is just out of the crosshair's reach (client side only).");
        leniency = configuration.getFloat(
            "leniency",
            QUARK,
            leniency,
            0.0F,
            1.0F,
            "How far (in blocks) beyond an edge you may aim and still have the block placed. Quark uses 0.5.");
        showIndicator = configuration.getBoolean(
            "showIndicator",
            QUARK,
            showIndicator,
            "Show the corner-bracket icon at the crosshair when a reach-around placement is possible.");

        edgeSnap = configuration.getBoolean(
            "enabled",
            EDGE,
            edgeSnap,
            "Edge snapping: when you aim at the very edge of a block face, the block is placed against the "
                + "neighbouring side instead. Needs to be installed on the server too.");
        edgeMargin = configuration.getFloat(
            "edgeMargin",
            EDGE,
            edgeMargin,
            0.01F,
            0.5F,
            "How close to the edge of a face (in blocks, 1.0 = whole face) the crosshair must be.");
        allowNearEdges = configuration.getBoolean(
            "allowNearEdges",
            EDGE,
            allowNearEdges,
            "If false, only edges away from you (or to your side) trigger edge snapping.");
        affectSideFaces = configuration.getBoolean(
            "affectSideFaces",
            EDGE,
            affectSideFaces,
            "If true, edge snapping also works on the walls of blocks, not only on floors and ceilings.");
        requireSneaking = configuration
            .getBoolean("requireSneaking", EDGE, requireSneaking, "If true, edge snapping only works while sneaking.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
