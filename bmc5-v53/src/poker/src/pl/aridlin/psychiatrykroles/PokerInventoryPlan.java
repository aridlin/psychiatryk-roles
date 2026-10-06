package pl.aridlin.psychiatrykroles;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Preflight insertion without dropping, mutating original stacks, or losing component identity. */
final class PokerInventoryPlan {
 static List<ItemStack> plan(List<ItemStack> original,ItemStack prototype,int count){
  if(count<=0||prototype.isEmpty())throw new IllegalArgumentException("Invalid delivery");
  var plan=new ArrayList<ItemStack>(original.size());for(var stack:original)plan.add(stack.copy());int left=count;
  for(var stack:plan)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,prototype)){
   int n=Math.min(left,Math.max(0,Math.min(stack.getMaxStackSize(),64)-stack.getCount()));stack.grow(n);left-=n;if(left==0)return plan;}
  for(int i=0;i<plan.size();i++)if(plan.get(i).isEmpty()){
   int n=Math.min(left,Math.min(prototype.getMaxStackSize(),64));plan.set(i,prototype.copyWithCount(n));left-=n;if(left==0)return plan;}
  return null;
 }
 private PokerInventoryPlan(){}
}
