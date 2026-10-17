package com.atenas.social;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Mod solo de cliente: amigos y chat de Atenas (mismo servicio que el launcher y el cliente 1.8.9). */
@Mod("atenassocial")
public class AtenasSocial {
    public AtenasSocial() {
        // Que el servidor no exija este mod
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) ClientInit.init();
    }
}
