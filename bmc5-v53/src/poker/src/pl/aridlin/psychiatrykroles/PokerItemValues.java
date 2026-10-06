package pl.aridlin.psychiatrykroles;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.*;
import java.util.*;
/** Pinned official ProjectE vanilla base/tag values, plus documented vanilla recipe arithmetic. */
final class PokerItemValues {
 private static final Map<String,Integer> DIRECT=new LinkedHashMap<>(),TAGS=new LinkedHashMap<>(),INSTRUMENTS=new LinkedHashMap<>();
 static {try(var in=PokerItemValues.class.getResourceAsStream("/data/psychiatryk_roles/poker/official-emc.json")){
  if(in==null)throw new IllegalStateException("Missing official EMC table");var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  read(json.getAsJsonObject("direct"),DIRECT);read(json.getAsJsonObject("tags"),TAGS);read(json.getAsJsonObject("instrument"),INSTRUMENTS);
 }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
 private static void read(com.google.gson.JsonObject json,Map<String,Integer> result){for(var e:json.entrySet()){int value=e.getValue().getAsInt();if(value<=0)throw new IllegalStateException("Invalid EMC");result.put(e.getKey(),value);}}
 static int value(Item item){String id=BuiltInRegistries.ITEM.getKey(item).toString();if(DIRECT.containsKey(id))return DIRECT.get(id);return item.builtInRegistryHolder().tags().map(k->TAGS.getOrDefault(k.location().toString(),0)).filter(n->n>0).min(Integer::compare).orElse(0);}
 static int value(ItemStack stack){if(stack.is(Items.GOAT_HORN)){var instrument=stack.get(net.minecraft.core.component.DataComponents.INSTRUMENT);return instrument==null?0:instrument.unwrapKey().map(k->INSTRUMENTS.getOrDefault(k.location().toString(),0)).orElse(0);}return value(stack.getItem());}
 static int value(String id){var key=net.minecraft.resources.ResourceLocation.tryParse(id);return key==null?0:BuiltInRegistries.ITEM.getOptional(key).map(PokerItemValues::value).orElse(0);}
 static Map<String,Integer> all(){var values=new TreeMap<String,Integer>();for(var item:BuiltInRegistries.ITEM){int value=value(item);if(value>0)values.put(BuiltInRegistries.ITEM.getKey(item).toString(),value);}return Map.copyOf(values);}
 static boolean canExchangeOut(String id){return value(id)>0;}
 static Map<String,Integer> exchangeOutItems(){return all();}
 private PokerItemValues(){}
}
