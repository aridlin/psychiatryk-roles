package pl.aridlin.parties;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
public final class TestZombie {
 private static final String OWNER="GoplanskaChamsOwner",PARTY="GoplanskaChamsParty";
 public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("chamstest").requires(s->s.hasPermission(2)).executes(c->{var p=c.getSource().getPlayerOrException();var level=p.serverLevel();int own=0;for(var entity:level.getAllEntities())if(entity instanceof Zombie&&entity.getPersistentData().hasUUID(OWNER)&&entity.getPersistentData().getUUID(OWNER).equals(p.getUUID()))own++;if(own>=3){c.getSource().sendFailure(Component.literal("You already have 3 test zombies. Use /chamstest clear."));return 0;}
  var direction=p.getLookAngle().multiply(1,0,1).normalize().scale(6);var target=p.position().add(direction);var pos=new BlockPos((int)Math.floor(target.x),p.getBlockY(),(int)Math.floor(target.z));if(!level.getBlockState(pos).canBeReplaced()||!level.getBlockState(pos.above()).canBeReplaced())pos=new BlockPos(pos.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ()),pos.getZ());var zombie=EntityType.ZOMBIE.create(level);if(zombie==null)return 0;zombie.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,p.getYRot(),0);zombie.setCustomName(Component.literal("Chams test"));zombie.setPersistenceRequired();zombie.setCanPickUpLoot(false);zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));zombie.setDropChance(EquipmentSlot.HEAD,0);zombie.getPersistentData().putUUID(OWNER,p.getUUID());zombie.getPersistentData().putInt(PARTY,GoplanskaParties.party(p));zombie.getPersistentData().putInt("GoplanskaChamsColor",0xff000000|(((net.deadlydiamond98.way.util.mixin.IWayPlayer)p).way$getColor()&0xffffff));level.addFreshEntity(zombie);c.getSource().sendSuccess(()->Component.literal("Test zombie summoned: marker always visible; halftone appears through cover and thickens with it. /chamstest clear removes your test zombies."),false);return 1;
 }).then(Commands.literal("clear").executes(c->{var p=c.getSource().getPlayerOrException();var remove=new ArrayList<Zombie>();for(var level:p.getServer().getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof Zombie z&&z.getPersistentData().hasUUID(OWNER)&&z.getPersistentData().getUUID(OWNER).equals(p.getUUID()))remove.add(z);remove.forEach(Zombie::discard);c.getSource().sendSuccess(()->Component.literal("Removed "+remove.size()+" of your test zombies."),false);return remove.size();})));}
 public static void append(ServerPlayer viewer,List<MemberSync.Member> members){int party=GoplanskaParties.party(viewer);for(var entity:viewer.serverLevel().getAllEntities())if(entity instanceof Zombie z&&z.getPersistentData().hasUUID(OWNER)&&(z.getPersistentData().getUUID(OWNER).equals(viewer.getUUID())||party>0&&party==z.getPersistentData().getInt(PARTY))){members.add(new MemberSync.Member(z.getUUID(),"Chams test",z.level().dimension().location().toString(),z.getX(),z.getY()+z.getBbHeight()+.5,z.getZ(),z.getPersistentData().getInt("GoplanskaChamsColor"),CoverThickness.between(viewer.serverLevel(),viewer.getEyePosition(),z.position().add(0,z.getBbHeight()*.5,0)),z.getId(),"zombie"));}}
}
