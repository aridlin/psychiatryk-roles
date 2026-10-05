package pl.aridlin.kukirin;
/** Bounded fuel arithmetic: ten coal fills an empty 72000-unit personal battery. */
public final class CoalChargePolicy {
 private static final int CAPACITY=72000,PER_COAL=CAPACITY/10;
 public static int required(int charge,int available){int missing=CAPACITY-Math.clamp(charge,0,CAPACITY);return Math.min(Math.max(0,available),(missing+PER_COAL-1)/PER_COAL);}
 public static int charged(int charge,int coal){return (int)Math.clamp((long)Math.clamp(charge,0,CAPACITY)+(long)Math.max(0,coal)*PER_COAL,0,CAPACITY);}
 private CoalChargePolicy(){}
}
