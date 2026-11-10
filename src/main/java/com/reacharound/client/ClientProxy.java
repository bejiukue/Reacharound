package com.reacharound.client;

import net.minecraftforge.common.MinecraftForge;

import com.reacharound.CommonProxy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        ClientHandler handler = new ClientHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }
}
