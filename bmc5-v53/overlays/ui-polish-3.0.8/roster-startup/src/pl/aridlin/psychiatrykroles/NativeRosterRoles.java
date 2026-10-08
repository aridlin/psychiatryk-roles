package pl.aridlin.psychiatrykroles;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Package access to native roles; identity comes only from the server-only plan. */
final class NativeRosterRoles {
 private NativeRosterRoles() {}
 static boolean builtinPatient(GameProfile profile) throws ReflectiveOperationException {
  Field ids=PsychiatrykRoles.class.getDeclaredField("LEGACY_PATIENTS"),names=PsychiatrykRoles.class.getDeclaredField("LEGACY_PATIENT_NAMES");
  ids.setAccessible(true);names.setAccessible(true);
  return ((Set<?>)ids.get(null)).contains(profile.getId()) || ((Set<?>)names.get(null)).contains(profile.getName().toLowerCase(Locale.ROOT));
 }
 static void validate(boolean operator,boolean builtinPatient) {
  if(operator)throw new IllegalArgumentException("Contractor assignment cannot change an operator");
  if(builtinPatient)throw new IllegalArgumentException("Built-in Patient cannot be converted by a roster plan");
 }
 static boolean matches(RoleData data,UUID target) {
  var rule=data.contractorRule(target);
  return !data.isPatient(target)&&rule!=null&&rule.mode().equals("always")&&rule.patient()==null&&rule.radius()==0;
 }
 @SuppressWarnings("unchecked")
 static void apply(RoleData data,UUID target) throws ReflectiveOperationException {
  // RoleData has no removePatient method. Remove exactly this UUID from the
  // existing native set rather than replacing/reloading unrelated SavedData.
  Field field=RoleData.class.getDeclaredField("patients");field.setAccessible(true);
  ((Set<UUID>)field.get(data)).remove(target);
  data.setContractor(target,new RoleData.ContractorRule("always",null,0));
  if(!matches(data,target))throw new IllegalStateException("Native Contractor assignment refused");
  data.setDirty();
 }
}
