package pl.aridlin.mountshotqa;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import com.vicmatskiv.pointblank.util.HitScan;
import com.vicmatskiv.pointblank.entity.SlowProjectile;
import pl.aridlin.kukirin.Kukirin;
import pl.aridlin.psychiatrykroles.pointblankfix.MountShotFilter;

@Mod("goplanska_mountshot_v32_qa")
public class MountShotServerQA {
    int ticks,checks;
    public MountShotServerQA(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::stopped);}
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("MOUNTSHOT_PASS "+label);}
    void stopped(ServerStoppedEvent event){try{Files.writeString(Path.of("trade-qa-stopped.txt"),"normal ServerStopped\n");}catch(Exception error){throw new RuntimeException(error);}}
    void tick(ServerTickEvent.Post event){
        if(++ticks!=20)return;Throwable failure=null;
        try{
            var world=event.getServer().overworld();world.setChunkForced(6,6,true);world.getChunkAt(new net.minecraft.core.BlockPos(100,100,100));for(var prior:world.getEntities((Entity)null,new AABB(95,90,95,110,115,120)))prior.discard();for(int x=99;x<=101;x++)for(int y=99;y<=104;y++)for(int z=99;z<=112;z++)world.setBlock(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);var shooter=EntityType.ZOMBIE.create(world);shooter.setNoAi(true);shooter.moveTo(100,100,100,0,0);world.addFreshEntity(shooter);
            var scooter=Kukirin.SCOOTER.get().create(world);scooter.moveTo(100,100,101,0,0);var item=new ItemStack(Kukirin.ITEM.get());CustomData.update(DataComponents.CUSTOM_DATA,item,tag->tag.putBoolean("GoplanskaScooterPassenger",true));scooter.setItemSlot(EquipmentSlot.FEET,item);world.addFreshEntity(scooter);
            var passenger=EntityType.ZOMBIE.create(world);passenger.setNoAi(true);passenger.moveTo(100,100,102,0,0);world.addFreshEntity(passenger);
            var outside=EntityType.ZOMBIE.create(world);outside.setNoAi(true);outside.moveTo(100,100,106,0,0);world.addFreshEntity(outside);
            check(shooter.startRiding(scooter,true)&&passenger.startRiding(scooter,true),"real scooter driver and co-passenger mount successfully");
            check(MountShotFilter.protectedFromOwnShot(shooter,scooter)&&MountShotFilter.protectedFromOwnShot(shooter,passenger)&&MountShotFilter.protectedFromOwnShot(shooter,shooter),"same-rig filter identifies scooter/self/co-passenger");
            check(!MountShotFilter.protectedFromOwnShot(shooter,outside),"outside living target stays eligible");
            var start=new Vec3(100,100.35,100);var direction=new Vec3(0,0,1);Predicate<Block> no=block->false;
            var hit=HitScan.getNearestObjectInCrosshair(shooter,start,direction,0,12,no,no,new ArrayList<>());
            System.out.println("MOUNTSHOT_TRACE hit="+hit+" expected="+outside+" shooterBox="+shooter.getBoundingBox()+" candidates="+world.getEntities(shooter,shooter.getBoundingBox().expandTowards(0,0,12)));check(hit instanceof EntityHitResult entity&&entity.getEntity()==outside,"actual mounted HitScan selects outside target beyond scooter and passenger");
            var validate=HitScan.class.getDeclaredMethod("ensureEntityInCrosshair",LivingEntity.class,Entity.class,Vec3.class,Vec3.class,float.class,double.class,float.class);validate.setAccessible(true);
            check(validate.invoke(null,shooter,scooter,start,direction,0f,12d,2f)==null&&validate.invoke(null,shooter,passenger,start,direction,0f,12d,2f)==null,"server supplied-target validation rejects own scooter/co-passenger");
            check(validate.invoke(null,shooter,outside,start,direction,0f,12d,2f)instanceof EntityHitResult,"server validation retains outside target");
            var projectile=new SlowProjectile((EntityType)EntityType.FIREBALL,world);projectile.setOwner(shooter);projectile.setPos(start);projectile.setDeltaMovement(direction);
            var canHit=SlowProjectile.class.getDeclaredMethod("canHitEntity",Entity.class);canHit.setAccessible(true);
            check(!(boolean)canHit.invoke(projectile,shooter)&&!(boolean)canHit.invoke(projectile,scooter)&&!(boolean)canHit.invoke(projectile,passenger),"actual SlowProjectile collision excludes own rig");
            check((boolean)canHit.invoke(projectile,outside),"actual SlowProjectile retains ordinary outside collision");
            var cache=SlowProjectile.class.getDeclaredField("hitScanTarget");cache.setAccessible(true);var trace=SlowProjectile.class.getDeclaredMethod("getHitResultOnMoveOrViewVector");trace.setAccessible(true);
            cache.set(projectile,new EntityHitResult(passenger,passenger.position()));trace.invoke(projectile);check(cache.get(projectile)==null,"near-owner cached co-passenger hit cannot bypass projectile collision filter");
            var outsideCached=new EntityHitResult(outside,outside.position());cache.set(projectile,outsideCached);trace.invoke(projectile);check(cache.get(projectile)==outsideCached,"outside cached projectile target is unchanged");
            passenger.stopRiding();shooter.stopRiding();shooter.moveTo(100,100,100,0,0);check(!MountShotFilter.protectedFromOwnShot(shooter,scooter)&&!MountShotFilter.protectedFromOwnShot(shooter,passenger),"dismount removes same-rig protection");
            var ordinaryHit=HitScan.getNearestObjectInCrosshair(shooter,start,direction,0,12,no,no,new ArrayList<>());check(ordinaryHit instanceof EntityHitResult entity&&entity.getEntity()==scooter,"ordinary dismounted hitscan can still target parked scooter");
            var list=new ArrayList<Entity>(List.of(scooter,passenger,outside));check(MountShotFilter.targets(shooter,list)==list,"ordinary query keeps original candidate list identity");
            var horse=EntityType.HORSE.create(world);world.addFreshEntity(horse);shooter.startRiding(horse,true);check(!MountShotFilter.protectedFromOwnShot(shooter,horse)&&MountShotFilter.targets(shooter,list)==list,"horse/other vehicle behavior is not globally filtered");shooter.stopRiding();
            for(var entity:List.of(shooter,scooter,passenger,outside,horse,projectile))entity.discard();
        }catch(Throwable error){failure=error;error.printStackTrace();}
        try{Files.writeString(Path.of("trade-qa-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",failure==null,"checks",checks,"error",failure==null?"":failure.toString())));}catch(Exception error){error.printStackTrace();}
        System.out.println("MOUNTSHOT_QA "+(failure==null?"SUCCESS":"FAILURE")+" "+checks+" checks");event.getServer().halt(false);
    }
}
