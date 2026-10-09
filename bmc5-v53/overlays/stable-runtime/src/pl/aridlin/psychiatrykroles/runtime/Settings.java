package pl.aridlin.psychiatrykroles.runtime;
import java.util.*;

/** Allowlisted bindings preserve the native config validators and atomic saves. */
public final class Settings {
    public record Spec(String owner,String field,double min,double max,double step,boolean toggle){}
    private static final String SCOOTER="pl.aridlin.kukirin.ScooterTuning",PEEB="pl.aridlin.psychiatrykroles.peeb.PeebConfig";
    public static final Map<String,Spec> BINDINGS=Map.ofEntries(
        e("scooter.steering",SCOOTER,"steering",.25,2,.05,false),e("scooter.grip",SCOOTER,"normalGrip",.5,1,.01,false),e("scooter.driftGrip",SCOOTER,"driftGrip",.03,.4,.01,false),e("scooter.recovery",SCOOTER,"recovery",.05,1,.01,false),e("scooter.tyreVolume",SCOOTER,"tyreVolume",0,1,.05,false),e("scooter.minimumTurnRate",SCOOTER,"minimumTurnRate",.5,6,.1,false),
        e("peeb.range",PEEB,"range",1,32,1,false),e("peeb.horizontalSpeed",PEEB,"maxHorizontalSpeed",.1,.7,.05,false),e("peeb.maxSpeed",PEEB,"maxSpeed",.1,1.2,.05,false),e("peeb.strength",PEEB,"strength",.05,1,.05,false),e("peeb.fallImmunity",PEEB,"fallImmunity",0,1,1,true),e("peeb.stopDistance",PEEB,"stopDistance",0,32,.1,false),e("peeb.grappleStep",PEEB,"grappleStep",0,1,1,true));
    private static Map.Entry<String,Spec> e(String id,String owner,String field,double min,double max,double step,boolean toggle){return Map.entry(id,new Spec(owner,field,min,max,step,toggle));}
    public static boolean known(String key){return BINDINGS.containsKey(key);}
    public static double read(String key)throws ReflectiveOperationException{var spec=BINDINGS.get(key);if(spec==null)throw new IllegalArgumentException("Unknown binding");var owner=Class.forName(spec.owner);var values=owner.getMethod("server").invoke(null);Object v=values.getClass().getMethod(spec.field).invoke(values);return v instanceof Boolean b?(b?1:0):((Number)v).doubleValue();}
    public static synchronized void write(String key,double value)throws ReflectiveOperationException{
        var spec=BINDINGS.get(key);if(spec==null||!Double.isFinite(value)||value<spec.min||value>spec.max||(spec.toggle&&value!=0&&value!=1))throw new IllegalArgumentException("Setting outside permitted range");
        var owner=Class.forName(spec.owner);var old=owner.getMethod("server").invoke(null);var type=old.getClass();var components=type.getRecordComponents();Object[] args=new Object[components.length];Class<?>[] types=new Class<?>[components.length];boolean found=false;
        for(int i=0;i<components.length;i++){var c=components[i];types[i]=c.getType();if(c.getName().equals(spec.field)){args[i]=spec.toggle?(Object)(value==1):(Object)value;found=true;}else args[i]=c.getAccessor().invoke(old);}
        if(!found)throw new IllegalArgumentException("Binding unavailable on this server version");var changed=type.getConstructor(types).newInstance(args);
        if(!(boolean)type.getMethod("valid").invoke(changed))throw new IllegalArgumentException("Native setting validation failed");owner.getMethod("save",type).invoke(null,changed);
    }
}
