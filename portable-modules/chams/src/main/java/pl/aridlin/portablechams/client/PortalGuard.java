package pl.aridlin.portablechams.client;

/** Optional compatibility: no linking or runtime dependency on Immersive Portals. */
final class PortalGuard {
    private static java.lang.reflect.Method method;
    private static boolean checked;
    static boolean nestedPortalRender() {
        if(!checked){checked=true;try{method=Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering",false,PortalGuard.class.getClassLoader()).getMethod("isRendering");}catch(ReflectiveOperationException ignored){}}
        if(method==null)return false;
        try{return Boolean.TRUE.equals(method.invoke(null));}catch(ReflectiveOperationException ignored){method=null;return false;}
    }
}
