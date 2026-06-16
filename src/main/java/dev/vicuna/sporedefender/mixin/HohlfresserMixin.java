package dev.vicuna.sporedefender.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.HohlMultipart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.Harbinger.Spore.Sentities.Calamities.Hohlfresser", remap = false)
abstract class HohlfresserMixin {
    @Redirect(
            method = "remove",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Harbinger/Spore/Sentities/BaseEntities/HohlMultipart;discard()V"
            )
    )
    private void sporedefender$discardPartIfPresent(HohlMultipart part) {
        if (part != null) {
            part.discard();
        }
    }
}
