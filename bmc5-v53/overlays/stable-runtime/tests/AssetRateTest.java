package pl.aridlin.psychiatrykroles.runtime;

import java.util.UUID;

/** Request and Ready share a bounded pre-parse budget for each player. */
public final class AssetRateTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args){
        UUID player=UUID.randomUUID(),other=UUID.randomUUID();long start=50_000_000_000L;
        check(AssetServer.admit(player,start),"Request admitted");
        check(AssetServer.admit(player,start),"Ready admitted in same tick");
        for(int i=0;i<6;i++)check(AssetServer.admit(player,start),"failed ACK retry slot "+i);
        check(!AssetServer.admit(player,start),"ninth packet dropped before JSON parse");
        check(!AssetServer.admit(player,start+999_999_999L),"window remains closed before one second");
        check(AssetServer.admit(other,start),"second player's budget independent");
        check(AssetServer.admit(player,start+1_000_000_000L),"budget resets at one second");
        check(AssetServer.admit(player,start-1),"monotonic clock reversal resets safely");
        System.out.println("ASSET_RATE_PASS checks="+checks);
    }
}
