package pl.aridlin.kukirin;

import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Edits one server snapshot; per-rider mouse steering and camera preferences remain personal. */
public final class ScooterAdminScreen extends Screen {
    private final double[] values=new double[6];
    private final double[] low={.25,.5,.03,.05,0,.5},high={2,1,.4,1,1,6};
    private final String[] names={"Steering speed multiplier","Normal tyre grip",
            "Ctrl drift grip (lower = more slide)","Grip recovery (higher = faster)",
            "Tyre scrub volume","High-speed minimum turn rate (deg/tick)"};
    public ScooterAdminScreen(ScooterTuning.Values values) {
        super(Component.literal("Shared server scooter tuning")); set(values);
    }
    public static void open(ScooterTuning.Values values) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ScooterAdminScreen(values));
    }
    private void set(ScooterTuning.Values snapshot) {
        values[0]=snapshot.steering(); values[1]=snapshot.normalGrip(); values[2]=snapshot.driftGrip();
        values[3]=snapshot.recovery(); values[4]=snapshot.tyreVolume(); values[5]=snapshot.minimumTurnRate();
    }
    @Override protected void init() {
        int span=Math.min(380,width-32),left=(width-span)/2;
        // Leave the Peeb button's Done row and the Music-category footer unobstructed.
        int row=Math.min(28,Math.max(16,(height-104)/8)),buttonHeight=Math.min(24,row);
        int panel=6*row+2*(buttonHeight+4),top=Math.max(28,(height-64-panel)/2);
        for(int i=0;i<values.length;i++) {
            final int index=i;
            addRenderableWidget(new AbstractSliderButton(left,top+i*row,span,row-4,Component.empty(),
                    (values[i]-low[i])/(high[i]-low[i])) {
                { updateMessage(); }
                protected void updateMessage() {
                    setMessage(Component.literal(names[index]+": "+String.format(Locale.ROOT,"%.2f",values[index])));
                }
                protected void applyValue() {
                    values[index]=low[index]+value*(high[index]-low[index]); updateMessage();
                }
            });
        }
        int applyY=top+6*row;
        addRenderableWidget(Button.builder(Component.literal("Apply to everyone & save"),button->{
            var snapshot=new ScooterTuning.Values(values[0],values[1],values[2],values[3],values[4],values[5]);
            if(snapshot.valid())net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterAdmin.Edit(true,snapshot));
        }).bounds(left,applyY,span/2-4,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Defaults"),button->{set(ScooterTuning.DEFAULT);rebuildWidgets();})
                .bounds(left+span/2+4,applyY,span/2-4,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),button->onClose())
                .bounds(left,applyY+buttonHeight+4,span,buttonHeight).build());
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float tick) {
        renderBackground(graphics,mx,my,tick); super.render(graphics,mx,my,tick);
        graphics.drawCenteredString(font,title,width/2,12,0xffffa537);
        graphics.drawCenteredString(font,Component.literal("Everyone, including new joiners. Live; no restart needed."),
                width/2,height-15,0xffdddddd);
    }
    @Override public boolean isPauseScreen() { return false; }
}
