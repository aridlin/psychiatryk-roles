package pl.aridlin.fixture;

import com.google.gson.JsonParser;
import java.util.Map;
import pl.aridlin.psychiatrykroles.runtime.client.VisualExpressions;

public final class VisualExpressionsTest {
    private static int checks;
    private static void check(boolean valid){checks++;if(!valid)throw new AssertionError("visual expression "+checks);}
    private static VisualExpressions.Expression compile(String raw){return VisualExpressions.compile(JsonParser.parseString(raw),new VisualExpressions.Budget());}
    private static void reject(String raw){try{compile(raw);throw new AssertionError("accepted "+raw);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        double[] values=new double[VisualExpressions.VARIABLE_COUNT];values[1]=0.45;values[12]=1;
        check(compile("{\"op\":\"and\",\"args\":[{\"var\":\"airborne\"},{\"op\":\"gt\",\"args\":[{\"var\":\"speed\"},0.3]}]}").evaluate(values)==1);
        values[1]=0.2;check(compile("{\"op\":\"gt\",\"args\":[{\"var\":\"speed\"},0.3]}").evaluate(values)==0);
        check(compile("{\"op\":\"mul\",\"args\":[{\"var\":\"speed\"},2]}").evaluate(values)==0.4);
        check(compile("{\"op\":\"div\",\"args\":[1,0]}").evaluate(values)==0);
        check(compile("{\"op\":\"clamp\",\"args\":[8,-2,2]}").evaluate(values)==2);
        check(compile("{\"op\":\"if\",\"args\":[{\"var\":\"airborne\"},30,0]}").evaluate(values)==30);
        reject("{\"var\":\"privateField\"}");reject("{\"op\":\"exec\",\"args\":[1]}");
        reject("{\"op\":\"add\",\"args\":[1]}");reject("{\"op\":\"rotate\",\"args\":[1,2]}");
        reject("1000001");reject("{\"op\":\"if\",\"args\":[1,2]}");
        check(VisualExpressions.compile(JsonParser.parseString("{\"var\":\"health\"}"),
            new VisualExpressions.Budget(),Map.of("health",17)).evaluate(values)==0);
        try{
            VisualExpressions.compile(JsonParser.parseString("{\"var\":\"speed\"}"),
                new VisualExpressions.Budget(),Map.of("health",17));
            throw new AssertionError("feature variable leak");
        }catch(IllegalArgumentException expected){checks++;}
        System.out.println("VISUAL_EXPRESSIONS_PASS checks="+checks);
    }
}
