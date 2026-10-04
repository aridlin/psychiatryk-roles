package pl.aridlin.psychiatrykroles;
/** Shared poker wallet, never substitute nuggets for real poker credit. */
public final class RentalPayment {
 public static boolean charge(net.minecraft.server.level.ServerPlayer player,long amount){return PokerData.get(player.getServer()).debit(player.getUUID(),amount);}
 public static void refund(net.minecraft.server.level.ServerPlayer player,long amount){PokerData.get(player.getServer()).credit(player.getUUID(),amount);}
 public static long balance(net.minecraft.server.level.ServerPlayer player){return PokerData.get(player.getServer()).balance(player.getUUID());}
}
