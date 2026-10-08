package pl.aridlin.kukirin;

public final class ScooterBatteryPolicy {
   public static final int CAPACITY = 72000;
   public static final long OFFLINE_FULL_MILLIS = 3600000L;

   private ScooterBatteryPolicy() {
   }

   public static double tickCost(double var0, int var2) {
      return Double.isFinite(var0) && !(var0 <= 0.0) ? 4.0 * Math.min(1.0, var0) / (double)(2 + Math.clamp((long)var2, 0, 3)) : 0.0;
   }

   public static ScooterBatteryPolicy.Drain drain(int var0, double var1, double var3) {
      var0 = Math.clamp((long)var0, 0, 72000);
      if (!Double.isFinite(var1) || var1 < 0.0 || var1 >= 1.0) {
         var1 = 0.0;
      }

      if (!Double.isFinite(var3) || var3 < 0.0) {
         var3 = 0.0;
      }

      double var5 = var3 + var1;
      long var7 = (long)Math.floor(var5 + 1.0E-9);
      int var9 = (int)Math.max(0L, (long)var0 - Math.min(var7, 72000L));
      double var10 = var9 == 0 ? 0.0 : Math.max(0.0, var5 - (double)var7);
      return new ScooterBatteryPolicy.Drain(var9, Math.min(Math.nextDown(1.0), var10));
   }

   public static int offlineCharge(int var0, long var1) {
      var0 = Math.clamp((long)var0, 0, 72000);
      long var3 = Math.clamp(var1, 0L, 3600000L);
      return Math.clamp((long)var0 + 72000L * var3 / 3600000L, 0, 72000);
   }

   public static record Drain(int charge, double fraction) {
   }
}
