package com.jonathan.chatoverhaul.mixin.rus;

import com.jonathan.chatoverhaul.client.CircuitChaseState;
import net.mcreator.interpritation.network.ThebrokenscriptModVariables.MapVariables;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
        "net.mcreator.interpritation.procedures.NoWayOut1DisplayOverlayIngameProcedure",
        "net.mcreator.interpritation.procedures.NoWayOut2DisplayOverlayIngameProcedure",
        "net.mcreator.interpritation.procedures.NoWayOut3DisplayOverlayIngameProcedure",
        "net.mcreator.interpritation.procedures.NoWayOut4DisplayOverlayIngameProcedure",
        "net.mcreator.interpritation.procedures.NoWayOut5DisplayOverlayIngameProcedure"
}, remap = false)
public class CircuitTextOverlayMixin {
    @Redirect(method = "execute", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/mcreator/interpritation/network/ThebrokenscriptModVariables$MapVariables;nowayoutframe:D"))
    private static double chatoverhaul$privateFrame(MapVariables ignored) {
        return CircuitChaseState.frame();
    }
}
