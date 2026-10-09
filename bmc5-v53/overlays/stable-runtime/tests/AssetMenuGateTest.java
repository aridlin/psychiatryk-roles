package pl.aridlin.psychiatrykroles.runtime;

import java.util.List;

/** Verifies the visible-only art gate and its finite fallback deadline. */
public final class AssetMenuGateTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args){
        var hidden=new Schema.Menu("Menu",0,List.of(
            new Schema.Entry("art","Operator art","image","none","","",0,0,0,2),
            new Schema.Entry("help","Help","label","none","","",0,0,0,0)));
        check(!RuntimeServer.hasVisibleImage(hidden,permission->permission<=0),"hidden image excluded from non-op gate");
        check(!RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=0,true,false,false,"main",null,10),
            "non-op menu opens immediately despite hidden art");
        check(RuntimeServer.hasVisibleImage(hidden,permission->permission<=2),"operator image is visible");
        check(RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,false,false,"main",null,10),
            "visible art waits during initial preload");
        var waiting=new RuntimeServer.PendingAssetMenu("main",100);
        check(RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,false,false,"main",waiting,99),
            "same menu still waits before deadline");
        check(!RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,false,false,"main",waiting,100),
            "same menu opens with placeholders at deadline");
        check(RuntimeServer.deadlinePassed(waiting.deadline(),100),"timer fallback runs at exact deadline");
        check(RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,false,false,"other",waiting,100),
            "new menu receives its own wait window");
        check(!RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,false,false,false,"main",null,10),
            "legacy client does not wait for unsupported asset channel");
        check(!RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,true,false,"main",null,10),
            "ready client opens immediately");
        check(!RuntimeServer.shouldDeferAssetMenu(hidden,permission->permission<=2,true,false,true,"main",null,10),
            "timeout continuation bypasses the art wait");
        System.out.println("ASSET_MENU_GATE_PASS checks="+checks);
    }
}
