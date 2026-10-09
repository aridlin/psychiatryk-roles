package pl.aridlin.psychiatrykroles.runtime.client;

/** Allocation-free, bounded evaluation shared by every HUD node type. */
public final class HudMotion {
    private HudMotion() {}

    public static boolean visible(VisualExpressions.Expression rule,double[] variables){
        return rule==null||rule.evaluate(variables)!=0;
    }

    public static double position(VisualExpressions.Expression rule,double fallback,double extent,double[] variables){
        double value=rule==null?fallback:rule.evaluate(variables);
        return Math.clamp(value,0,1-extent);
    }

    public static double progress(VisualExpressions.Expression rule,double fallback,double[] variables){
        return Math.clamp(rule==null?fallback:rule.evaluate(variables),0,1);
    }
}
