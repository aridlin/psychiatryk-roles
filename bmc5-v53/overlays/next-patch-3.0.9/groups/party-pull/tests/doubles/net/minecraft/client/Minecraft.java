package net.minecraft.client;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
/** Test-only window-free singleton. No rendering or options are instantiated. */
public final class Minecraft {
 public static final Minecraft INSTANCE=new Minecraft();
 public LocalPlayer player;public ClientLevel level;
 public static Minecraft getInstance(){return INSTANCE;}
 public Entity getCameraEntity(){return player;}
 public boolean isPaused(){return false;}
}
