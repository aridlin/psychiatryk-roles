import pl.aridlin.psychiatrykroles.runtime.HudSchema;

/** Run with the unchanged 3.0.10 JAR first on the classpath. */
public final class HudLegacyCompatibilityTest {
    public static void main(String[] args){
        if(HudSchema.Node.class.getRecordComponents().length!=12)
            throw new AssertionError("This test must load the old 3.0.10 HUD client class");
        String raw="{\"schema\":1,\"channel\":\"hud\",\"op\":\"replace\",\"id\":\"status\","+
            "\"scene\":{\"id\":\"status\",\"nodes\":[{"+
            "\"id\":\"health\",\"type\":\"progress\",\"x\":0.1,\"y\":0.2,\"w\":0.3,\"h\":0.04,"+
            "\"color\":-1,\"background\":0,\"value\":0.5,"+
            "\"visibleWhen\":\"{\\\"var\\\":\\\"airborne\\\"}\",\"xRule\":\"0.2\"}]}}";
        var parsed=HudSchema.parse(raw);
        var node=parsed.scene().nodes().get(0);
        if(node.x()!=.1||node.value()!=.5||!"health".equals(node.id()))
            throw new AssertionError("Legacy fallback changed");
        System.out.println("HUD_LEGACY_COMPAT_PASS");
    }
}
