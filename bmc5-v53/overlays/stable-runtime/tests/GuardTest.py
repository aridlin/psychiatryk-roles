from pathlib import Path
import importlib.util,struct,tempfile,zipfile

root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('guard',root/'server_only_guard.py')
guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)
checks=0

def check(value,message):
 global checks
 checks+=1
 assert value,message

def classfile(name,*,refs=(),tokens=(),methods=()):
 """Tiny structurally valid Java 21 classfile, with configurable constant-pool links."""
 pool=[]
 def utf(value):
  raw=value.encode();pool.append(b'\x01'+struct.pack('>H',len(raw))+raw);return len(pool)
 def cls(value):
  index=utf(value);pool.append(b'\x07'+struct.pack('>H',index));return len(pool)
 this=cls(name);parent=cls('java/lang/Object')
 for ref in refs:cls(ref)
 for token in tokens:utf(token)
 entries=[]
 for member,descriptor,flags in methods:entries.append((utf(member),utf(descriptor),flags))
 raw=b'\xca\xfe\xba\xbe'+struct.pack('>HHH',0,61,len(pool)+1)+b''.join(pool)
 raw+=struct.pack('>HHHH',0x21,this,parent,0)+struct.pack('>H',0)
 raw+=struct.pack('>H',len(entries))
 for member,descriptor,flags in entries:raw+=struct.pack('>HHHH',flags,member,descriptor,0)
 return raw+struct.pack('>H',0)

def write(path,entries):
 with zipfile.ZipFile(path,'w') as archive:
  for name,data in entries.items():archive.writestr(name,data)

def rejected(client,server,entries,message):
 write(server,entries)
 try:guard.compare(client,server)
 except ValueError:return check(True,message)
 raise AssertionError(message)

with tempfile.TemporaryDirectory() as temp:
 client=Path(temp)/'client.jar';server=Path(temp)/'server.jar'
 prefix='pl/aridlin/psychiatrykroles/'
 runtime=prefix+'runtime/'
 peeb=prefix+'peeb/PeebGrapple'
 game=prefix+'PokerGame'
 entries={
 'META-INF/neoforge.mods.toml':b'fixed',
 runtime+'RuntimeNetwork.class':classfile(runtime+'RuntimeNetwork'),
 runtime+'RuntimeServer.class':classfile(runtime+'RuntimeServer'),
 runtime+'AssetServer.class':classfile(runtime+'AssetServer'),
 runtime+'HudServer.class':classfile(runtime+'HudServer',refs=(runtime+'AssetServer',)),
 peeb+'.class':classfile(peeb),
  prefix+'VoidDoors.class':classfile(prefix+'VoidDoors'),
  game+'.class':classfile(game,methods=[('run','()V',0)]),
  'pl/aridlin/kukirin/ScooterRentalSpawner.class':classfile('pl/aridlin/kukirin/ScooterRentalSpawner'),
  'pl/aridlin/kukirin/ScooterTuning.class':classfile('pl/aridlin/kukirin/ScooterTuning'),
  prefix+'client/FakeClientScreen.class':classfile(prefix+'client/FakeClientScreen',
      refs=(peeb,prefix+'VoidDoors','pl/aridlin/kukirin/ScooterTuning','net/minecraft/client/gui/screens/Screen')),
  'psychiatryk-runtime-default.json':b'{}',
 }
 write(client,entries)
 check(guard.compare(client,client)==[],'unchanged jar accepted')
 check(guard.allowed(game+'.class') and guard.allowed(prefix+'VoidDoorData.class'),
       'curated poker and void server classes listed')
 check(guard.allowed('pl/aridlin/kukirin/ScooterRentalSpawner.class')
       and guard.allowed(prefix+'VillagerTradeRebalance.class') and guard.allowed(prefix+'RoleData.class'),
       'scooter, villager and role server classes listed')
 check(guard.allowed(peeb+'.class') and not guard.allowed(prefix+'VoidDoors.class')
       and not guard.allowed('pl/aridlin/kukirin/ScooterTuning.class'),
       'grapple is curated, while door core and tuning stay frozen')
 write(server,entries|{runtime+'RuntimeServer.class':classfile(runtime+'RuntimeServer',tokens=('implementation-v2',))})
 check(guard.compare(client,server)==[runtime+'RuntimeServer.class'],'server runtime implementation accepted')
 write(server,entries|{runtime+'AssetServer.class':classfile(runtime+'AssetServer',tokens=('new-asset-policy',))})
 check(guard.compare(client,server)==[runtime+'AssetServer.class'],
       'server-only AssetServer change accepted despite HudServer reference')
 hud_client=runtime+'client/HudClient'
 client_with_hud=entries|{hud_client+'.class':classfile(hud_client,refs=(runtime+'AssetServer','net/minecraft/client/Minecraft'))}
 write(client,client_with_hud)
 rejected(client,server,client_with_hud|{runtime+'AssetServer.class':classfile(runtime+'AssetServer',tokens=('new-asset-policy',))},
          'real client HUD reference still freezes AssetServer')
 rejected(client,server,client_with_hud|{hud_client+'.class':classfile(hud_client,refs=(runtime+'AssetServer','net/minecraft/client/Minecraft'),tokens=('new-hud-rendering',))},
          'client HudClient change rejected')
 write(client,entries)
 write(server,entries|{game+'.class':classfile(game,methods=[('run','()V',0)],tokens=('new-poker-rules',))})
 check(guard.compare(client,server)==[game+'.class'],'server poker behavior accepted')
 scooter='pl/aridlin/kukirin/ScooterRentalSpawner'
 write(server,entries|{scooter+'.class':classfile(scooter,tokens=('new-spawn-policy',))})
 check(guard.compare(client,server)==[scooter+'.class'],'server scooter spawn policy accepted')
 rejected(client,server,entries|{runtime+'RuntimeNetwork.class':classfile(runtime+'RuntimeNetwork',tokens=('new-wire',))},
          'network change rejected')
 rejected(client,server,entries|{peeb+'.class':classfile(peeb,tokens=('changed-shared-physics',))},
          'client-used PeebGrapple change rejected')
 unlinked=entries|{prefix+'client/FakeClientScreen.class':classfile(prefix+'client/FakeClientScreen',
     refs=(prefix+'VoidDoors','pl/aridlin/kukirin/ScooterTuning','net/minecraft/client/gui/screens/Screen'))}
 write(client,unlinked)
 write(server,unlinked|{peeb+'.class':classfile(peeb,tokens=('changed-server-grapple',))})
 check(guard.compare(client,server)==[peeb+'.class'],
       'binary-compatible PeebGrapple implementation accepted without client roots')
 rejected(client,server,unlinked|{peeb+'.class':classfile(peeb,
     refs=('net/minecraft/network/protocol/common/custom/CustomPacketPayload',))},
          'PeebGrapple wire-format reference still rejected')
 shaped=unlinked|{peeb+'.class':classfile(peeb,methods=[('existing','()V',0)])}
 write(client,shaped)
 rejected(client,server,unlinked,'grapple member removal rejected')
 write(client,entries)
 rejected(client,server,entries|{prefix+'VoidDoors.class':classfile(prefix+'VoidDoors',tokens=('changed-registration',))},
          'void core change rejected')
 rejected(client,server,entries|{game+'.class':classfile(game,methods=[],tokens=('method-removed',))},
          'binary method removal rejected')
 write(server,entries|{game+'.class':classfile(game,methods=[('run','()V',0),('extra','()V',0)])})
 check(guard.compare(client,server)==[game+'.class'],'additive server method accepted')
 bridge=runtime+'server/Bridge'
 service=guard.SERVER_SERVICE
 write(server,entries|{bridge+'.class':classfile(bridge),service:b'pl.aridlin.psychiatrykroles.runtime.server.Bridge\n'})
 check(len(guard.compare(client,server))==2,'new server feature and provider accepted')
 rejected(client,server,entries|{service:b'pl.aridlin.psychiatrykroles.runtime.server.Missing\n'},
          'missing service provider rejected')
 rejected(client,server,entries|{bridge+'.class':classfile(bridge,refs=('net/minecraft/client/Minecraft',))},
          'server feature client-only reference rejected')
 rejected(client,server,entries|{bridge+'.class':classfile(bridge,refs=('net/neoforged/neoforge/registries/DeferredRegister',))},
          'new registry bootstrap rejected')
 rejected(client,server,entries|{bridge+'.class':classfile(bridge,refs=('net/minecraft/network/protocol/common/custom/CustomPacketPayload',))},
          'new payload definition rejected')
 unsafe=runtime+'server/HiddenPayload'
 rejected(client,server,entries|{unsafe+'.class':classfile(unsafe)},
          'payload-like name rejected even without a codec reference')
 rejected(client,server,entries|{bridge+'.class':b'not a classfile'},'invalid classfile rejected')
 rejected(client,server,entries|{runtime+'server/Bridge.class':classfile(runtime+'server/Impostor')},
          'classfile/path mismatch rejected')
 rejected(client,server,entries|{'psychiatryk-runtime-default.json':b'not json'},
          'invalid runtime menu JSON rejected')
 rejected(client,server,{name:data for name,data in entries.items() if name!=runtime+'RuntimeServer.class'},
          'baseline class deletion rejected')
 referenced=prefix+'client/AnotherClientScreen'
 client_refs=entries|{referenced+'.class':classfile(referenced,refs=(game,))}
 write(client,client_refs)
 rejected(client,server,client_refs|{game+'.class':classfile(game,methods=[('run','()V',0)],tokens=('changed',))},
          'otherwise allowed class referenced by installed client rejected')

print(f'PASS {checks} client-freeze release-gate checks')
