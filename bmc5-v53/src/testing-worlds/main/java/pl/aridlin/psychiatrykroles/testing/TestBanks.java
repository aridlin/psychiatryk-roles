package pl.aridlin.psychiatrykroles.testing;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Durable pre-swap ledger; the active player file remains authoritative between swaps. */
public final class TestBanks extends SavedData {
 public static final String NAME="psychiatryk_testing_banks";
 private CompoundTag players=new CompoundTag();
 public static TestBanks get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(TestBanks::new,TestBanks::load),NAME);}
 public static TestBanks load(CompoundTag t,HolderLookup.Provider p){var d=new TestBanks();d.players=t.getCompound("players").copy();return d;}
 @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putInt("schema",1);t.put("players",players.copy());return t;}
 public CompoundTag entry(UUID id){String k=id.toString();if(!players.contains(k)){players.put(k,new CompoundTag());setDirty();}return players.getCompound(k);}
 public synchronized void durable(MinecraftServer s)throws IOException {
  var root=new CompoundTag();root.put("data",save(new CompoundTag(),s.registryAccess()));root.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
  Path dir=s.getWorldPath(LevelResource.ROOT).resolve("data");Files.createDirectories(dir);atomic(root,dir.resolve(NAME+".dat"));setDirty();
 }
 @Override public synchronized void save(java.io.File file,HolderLookup.Provider provider){
  if(!isDirty())return;var root=new CompoundTag();root.put("data",save(new CompoundTag(),provider));root.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
  try{Files.createDirectories(file.toPath().getParent());atomic(root,file.toPath());setDirty(false);}catch(IOException error){com.mojang.logging.LogUtils.getLogger().error("Could not atomically save testing inventory banks; retained dirty state",error);}
 }
 public static void atomic(CompoundTag root,Path path)throws IOException{
  Path tmp=path.resolveSibling(path.getFileName()+".next");NbtIo.writeCompressed(root,tmp);
  try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
  // No non-atomic fallback: the caller aborts before changing a player's bank.
  Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  try(var directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}
 }
}
