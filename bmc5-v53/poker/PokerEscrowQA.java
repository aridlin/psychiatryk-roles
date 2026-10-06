package pl.aridlin.psychiatrykroles;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class PokerEscrowQA {
 static int checks;static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static List<ItemStack> empty(){var list=new ArrayList<ItemStack>();for(int i=0;i<36;i++)list.add(ItemStack.EMPTY);return list;}
 static void fails(Runnable action,String label){boolean threw=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException|ArithmeticException expected){threw=true;}check(threw,label);}
 public static void main(String[] args)throws Exception{
  net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  UUID a=UUID.nameUUIDFromBytes("fixture-a".getBytes()),b=UUID.nameUUIDFromBytes("fixture-b".getBytes());
  var data=new PokerData();check(data.stock().isEmpty(),"No stock synthesized");
  fails(()->data.credit(a,1),"Unbacked credit cannot mint wallet");
  var diamond=new ItemStack(Items.DIAMOND,5);diamond.set(DataComponents.CUSTOM_NAME,Component.literal("Exact deposited gem"));
  var original=diamond.copy();long added=data.deposit(a,diamond,3,PokerItemValues.value(diamond));
  check(added==24576&&diamond.getCount()==2,"Exact official diamond deposit");check(data.stockValue()==added&&data.balance(a)==added,"Backing equals credited EMC");
  var lot=data.stock().getFirst();check(lot.count()==3&&ItemStack.isSameItemSameComponents(lot.sample(),original),"Name/component prototype intact");
  var alias=lot.sample();alias.set(DataComponents.CUSTOM_NAME,Component.literal("mutated view"));check(!ItemStack.isSameItemSameComponents(alias,data.stock().getFirst().sample()),"View copies cannot mutate stock");
  var inventory=empty();check(data.withdraw(b,lot.id(),1,inventory)==0,"Other empty wallet cannot withdraw");check(data.withdraw(a,lot.id(),4,inventory)==0,"No phantom stock");
  var full=new ArrayList<ItemStack>();for(int i=0;i<36;i++)full.add(new ItemStack(Items.COBBLESTONE,64));long before=data.balance(a);
  check(data.withdraw(a,lot.id(),1,full)==0&&data.balance(a)==before&&data.stock().getFirst().count()==3,"Full inventory rejects atomically");
  check(data.withdraw(a,lot.id(),2,inventory)==2,"Exact lot withdraw");check(inventory.getFirst().getCount()==2&&ItemStack.isSameItemSameComponents(inventory.getFirst(),original),"Withdrawal preserves original item data");
  check(data.stockValue()==8192&&data.balance(a)==8192,"No item or credit loss");check(data.withdraw(a,lot.id(),1,inventory)==1&&data.stock().isEmpty()&&data.balance(a)==0,"Stock exhausted cannot mint another item");
  check(data.withdraw(a,lot.id(),1,inventory)==0,"Stale GUI lot cannot create item");
  var iron=new ItemStack(Items.IRON_INGOT,64);data.deposit(a,iron,64,256);check(data.stockValue()==16384,"Official iron EMC");
  var second=new ItemStack(Items.IRON_INGOT,32);data.deposit(b,second,32,256);check(data.stock().size()==1&&data.stock().getFirst().count()==96,"Shared lots combine exact identity across players");
  long shared=data.stock().getFirst().id();var invB=empty();check(data.withdraw(b,shared,32,invB)==32,"Deposited stock is server-wide");check(data.balance(b)==0&&data.stock().getFirst().count()==64,"Owner-independent stock accounting");
  var different=new ItemStack(Items.IRON_INGOT,1);different.set(DataComponents.CUSTOM_NAME,Component.literal("Different"));data.deposit(a,different,1,256);check(data.stock().size()==2,"Distinct components stay distinct lots");
  long total=data.stockValue(),wallet=data.balance(a);var failed=new ItemStack(Items.DIAMOND,2);
  fails(()->data.deposit(a,failed,2,Long.MAX_VALUE),"Overflow rejected before mutation");check(failed.getCount()==2&&data.stockValue()==total&&data.balance(a)==wallet,"Overflow leaves stock/wallet/item unchanged");
  var damaged=new ItemStack(Items.IRON_PICKAXE);damaged.setDamageValue(27);damaged.set(DataComponents.CUSTOM_NAME,Component.literal("Used tool"));data.deposit(a,damaged,1,768);var tool=data.stock().getLast();var toolInv=empty();check(data.withdraw(a,tool.id(),1,toolInv)==1&&toolInv.getFirst().getDamageValue()==27,"Used item never laundered into a pristine replacement");
  var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
  var serialized=data.save(new net.minecraft.nbt.CompoundTag(),registries);var loaded=PokerData.load(serialized,registries);
  check(loaded.stockValue()==data.stockValue()&&loaded.balance(a)==data.balance(a),"Registry-aware persistent ledger roundtrip");
  for(int i=0;i<data.stock().size();i++)check(ItemStack.isSameItemSameComponents(loaded.stock().get(i).sample(),data.stock().get(i).sample()),"Roundtrip exact stock components "+i);
  var corrupted=serialized.copy();corrupted.getList("Wallets",10).getCompound(0).putLong("Chips",Long.MAX_VALUE);fails(()->PokerData.load(corrupted,registries),"Unbacked corrupt saved ledger rejected");
  var legacy=new net.minecraft.nbt.CompoundTag();var wallets=new net.minecraft.nbt.ListTag();var old=new net.minecraft.nbt.CompoundTag();old.putUUID("Player",a);old.putLong("Chips",1000);wallets.add(old);legacy.put("Wallets",wallets);var migrated=PokerData.load(legacy,registries);check(migrated.isDirty(),"Migration will persist quarantine schema");check(migrated.balance(a)==0&&migrated.stock().isEmpty(),"Legacy unbacked credits quarantined");check(migrated.save(new net.minecraft.nbt.CompoundTag(),registries).getList("LegacyWallets",10).size()==1,"Legacy balance retained for audit, not redemption");
  var bots=new PokerData();var stake=new ItemStack(Items.IRON_INGOT,1);bots.deposit(a,stake,1,256);var game=bots.create("fixture",a,"Human");game.buyIn(a,256);bots.debit(a,256);game.addBots(a,4,1000000);check(game.redeemableReserve()==256,"Free bots add no redeemable reserve");game.player(a).chips=4_000_256;var cash=game.cashOut(a);check(cash.paid()==256&&cash.houseChipsExpired()==4_000_000,"House winnings cannot mint value");bots.credit(a,cash.paid());check(bots.liabilities()==bots.stockValue(),"Bot cashout respects escrow backing");var botId=game.players().stream().filter(p->p.bot).findFirst().get().id;var botCash=game.cashOut(botId);check(botCash.paid()==0,"Bots cannot cashout redeemable credits");
  var random=new Random(7);var randomData=new PokerData();var randInv=empty();long received=0;
  for(int i=0;i<1000;i++){
   int n=1+random.nextInt(64);var s=new ItemStack(Items.GOLD_INGOT,n);received+=n;randomData.deposit(a,s,n,2048);
   var entry=randomData.stock().getFirst();int requested=1+random.nextInt((int)Math.min(entry.count(),64));int result=randomData.withdraw(a,entry.id(),requested,randInv);
   long stored=randomData.stock().stream().mapToLong(PokerData.StockView::count).sum();long delivered=randInv.stream().mapToLong(ItemStack::getCount).sum();
   check(stored+delivered==received,"Random item conservation "+i);check(randomData.stockValue()==randomData.balance(a),"Random EMC conservation "+i);
   if(i%8==0)randInv=empty(); // delivered values remain outside bank; update fixture received to outstanding+current inventory.
   if(i%8==0)received=stored;
  }
  System.out.println("POKER_ESCROW_QA_PASS checks="+checks);
 }
}
