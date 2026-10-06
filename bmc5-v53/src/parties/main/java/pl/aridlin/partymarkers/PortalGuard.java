package pl.aridlin.partymarkers;
final class PortalGuard {
    private static java.lang.reflect.Method method;private static boolean checked;
    static boolean rendering(){
        if(!checked){checked=true;try{method=Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering",false,PortalGuard.class.getClassLoader()).getMethod("isRendering");}catch(ReflectiveOperationException ignored){}}
        if(method==null)return false;
        try{return Boolean.TRUE.equals(method.invoke(null));}catch(ReflectiveOperationException ignored){method=null;return false;}
    }
}
