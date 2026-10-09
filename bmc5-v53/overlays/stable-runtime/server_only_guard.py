"""Conservative release gate: preserve the frozen client, registries and protocol.

This validates a future server-only JAR against the installed client baseline.
It does not publish anything or claim arbitrary server code is safe.
"""
from pathlib import Path
import argparse,hashlib,json,struct,zipfile

# Exact 3.0.9 families with server-owned behavior. The generic client-visible
# classes (registries, payloads, entities, menus, renderers) stay frozen.
SERVER_FAMILIES = {
    # The 3.0.10 runtime's one-time client install leaves these implementations server-owned.
    'pl/aridlin/psychiatrykroles/runtime/RuntimeServer',
    'pl/aridlin/psychiatrykroles/runtime/BehaviorEngine',
    'pl/aridlin/psychiatrykroles/runtime/AssetServer',
    'pl/aridlin/psychiatrykroles/runtime/Settings',
    'pl/aridlin/psychiatrykroles/runtime/FallbackMenu',
    # The 3.0.10 client calls frozen PeebSharedPhysics instead of this server implementation.
    'pl/aridlin/psychiatrykroles/peeb/PeebGrapple',
    # Void world state, teleport math, and server-side display geometry.
    'pl/aridlin/psychiatrykroles/ImmersiveVoidPortals',
    'pl/aridlin/psychiatrykroles/VoidDoorData',
    'pl/aridlin/psychiatrykroles/VoidTrapdoorData',
    'pl/aridlin/psychiatrykroles/PortalSweep',
    'pl/aridlin/psychiatrykroles/PortalFrameOcclusion',
    'pl/aridlin/psychiatrykroles/VoidExitDirections',
    'pl/aridlin/psychiatrykroles/VoidDoorGeometry',
    # Poker state/economy/commands; PokerMenu, PokerScreen and cards stay frozen.
    'pl/aridlin/psychiatrykroles/PokerActivity',
    'pl/aridlin/psychiatrykroles/PokerCommands',
    'pl/aridlin/psychiatrykroles/PokerData',
    'pl/aridlin/psychiatrykroles/PokerDepositPolicy',
    'pl/aridlin/psychiatrykroles/PokerGame',
    'pl/aridlin/psychiatrykroles/PokerHandEvaluator',
    'pl/aridlin/psychiatrykroles/PokerInventoryPlan',
    'pl/aridlin/psychiatrykroles/PokerItemValues',
    # Villager offers, role persistence and server schedules.
    'pl/aridlin/psychiatrykroles/VillagerTradeRebalance',
    'pl/aridlin/psychiatrykroles/SeasonProgression',
    'pl/aridlin/psychiatrykroles/RoleData',
    'pl/aridlin/psychiatrykroles/RosterStartup',
    'pl/aridlin/psychiatrykroles/NativeRosterRoles',
    'pl/aridlin/psychiatrykroles/PsychiatrykHelpCommands',
    'pl/aridlin/psychiatrykroles/RestartManager',
    'pl/aridlin/psychiatrykroles/RestartCountdown',
    'pl/aridlin/psychiatrykroles/RestartHistory',
    'pl/aridlin/psychiatrykroles/OldWorldCommands',
    'pl/aridlin/psychiatrykroles/OldWorldImport',
    'pl/aridlin/psychiatrykroles/OldWorldTravelData',
    # Server-side scooter rental spawn/payment policy, not its entity/prediction/music UI.
    'pl/aridlin/kukirin/ScooterRentalSpawner',
    'pl/aridlin/kukirin/RentalPayments',
}
SERVER_PACKAGES = ('pl/aridlin/psychiatrykroles/runtime/server/',
                   'pl/aridlin/psychiatrykroles/runtime/grapple/')
SERVER_SERVICE = 'META-INF/services/pl.aridlin.psychiatrykroles.runtime.ServerFeature'
REGISTRY_OR_WIRE_MARKERS = (
    b'net/neoforged/fml/common/Mod',
    b'net/neoforged/neoforge/registries/DeferredRegister',
    b'net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent',
    b'net/neoforged/neoforge/network/registration/PayloadRegistrar',
    b'net/minecraft/network/protocol/common/custom/CustomPacketPayload',
    b'net/minecraft/network/codec/StreamCodec',
)
UNSAFE_CLASS_NAME_PARTS = ('Client','Screen','Renderer','Payload','Packet','Network',
                           'Registry','Registration','Recipe','Mixin')

def _class_info(data):
    """Read enough of a classfile to freeze binary shape and find client references."""
    if len(data)<10 or data[:4]!=b'\xca\xfe\xba\xbe':raise ValueError('Invalid classfile')
    pos=8
    def take(size):
        nonlocal pos
        if pos+size>len(data):raise ValueError('Truncated classfile')
        result=data[pos:pos+size];pos+=size;return result
    def u2():return struct.unpack('>H',take(2))[0]
    def u4():return struct.unpack('>I',take(4))[0]
    pool=[None]*u2();i=1
    while i<len(pool):
        tag=take(1)[0]
        if tag==1:pool[i]=(tag,take(u2()).decode('utf-8','replace'))
        elif tag in (3,4):take(4)
        elif tag in (5,6):take(8);i+=1
        elif tag in (7,8,16,19,20):pool[i]=(tag,u2())
        elif tag in (9,10,11,12,17,18):pool[i]=(tag,u2(),u2())
        elif tag==15:pool[i]=(tag,take(1)[0],u2())
        else:raise ValueError('Unknown classfile constant tag '+str(tag))
        i+=1
    def utf(index):
        value=pool[index]
        if value is None or value[0]!=1:raise ValueError('Invalid UTF-8 constant index')
        return value[1]
    def cls(index):
        if not index:return ''
        value=pool[index]
        if value is None or value[0]!=7:raise ValueError('Invalid class constant index')
        return utf(value[1])
    access=u2();this=cls(u2());parent=cls(u2())
    interfaces=tuple(cls(u2()) for _ in range(u2()))
    def members():
        result=set()
        for _ in range(u2()):
            flags=u2();name=utf(u2());desc=utf(u2());result.add((name,desc,flags))
            for _ in range(u2()):u2();take(u4())
        return result
    fields=members();methods=members()
    for _ in range(u2()):u2();take(u4())
    if pos!=len(data):raise ValueError('Trailing classfile bytes')
    return {'name':this,'parent':parent,'interfaces':interfaces,'access':access,
            'fields':fields,'methods':methods,
            'utf8':{entry[1] for entry in pool if entry is not None and entry[0]==1}}

def _client_root(name,data):
    # HudServer owns server-side scene validation and packet sends. Its name
    # contains "Hud", but it is not an installed-client entry point. Keep the
    # generic HUD heuristic for every other class, including HudClient.
    if name=='pl/aridlin/psychiatrykroles/runtime/HudServer.class':return False
    leaf=name.rsplit('/',1)[-1].lower()
    return (b'net/minecraft/client/' in data or '/client/' in name.lower()
            or any(word in leaf for word in ('client','renderer','screen','hud'))
            or (b'net/neoforged/api/distmarker/Dist' in data and b'CLIENT' in data))

def _references(info,target):
    return target in info['utf8'] or any('L'+target+';' in value or 'L'+target+'<' in value
                                          for value in info['utf8'])

def _binary_compatible(before,after):
    return (before['name']==after['name'] and before['parent']==after['parent']
            and before['interfaces']==after['interfaces'] and before['access']==after['access']
            and before['fields']<=after['fields'] and before['methods']<=after['methods'])

def allowed(name):
    server_class=name.startswith(SERVER_PACKAGES) and name.endswith('.class')
    return name in ('psychiatryk-runtime-default.json',SERVER_SERVICE) or server_class or any(
        name==p+'.class' or name.startswith(p+'$') and name.endswith('.class') for p in SERVER_FAMILIES)

def compare(client,server):
    with zipfile.ZipFile(client) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    with zipfile.ZipFile(server) as z:new={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    changed=sorted(n for n in old.keys()|new.keys() if old.get(n)!=new.get(n))
    forbidden=[n for n in changed if not allowed(n) or n not in new]
    if forbidden:raise ValueError('Client update or explicit compatibility review required: '+', '.join(forbidden))
    if SERVER_SERVICE in new:
        providers=[line.strip() for line in new[SERVER_SERVICE].decode().splitlines() if line.strip() and not line.lstrip().startswith('#')]
        if len(providers)>16 or len(providers)!=len(set(providers)) or any(
                not name.startswith('pl.aridlin.psychiatrykroles.runtime.server.')
                or name.replace('.','/')+'.class' not in new for name in providers):
            raise ValueError('Invalid server feature provider')
    if 'psychiatryk-runtime-default.json' in changed:
        json.loads(new['psychiatryk-runtime-default.json'])
    client_roots=[_class_info(data) for name,data in old.items()
                  if name.endswith('.class') and _client_root(name,data)]
    for name in changed:
        if not name.endswith('.class'):continue
        if any(part in name.rsplit('/',1)[-1] for part in UNSAFE_CLASS_NAME_PARTS):
            raise ValueError('Client, registry, or wire class name requires review: '+name)
        code=new[name]
        if b'net/minecraft/client/' in code:raise ValueError('Server feature references client-only classes: '+name)
        if any(marker in code for marker in REGISTRY_OR_WIRE_MARKERS):
            raise ValueError('Server feature touches registration or wire format: '+name)
        after=_class_info(code);internal=name[:-6]
        if after['name']!=internal:raise ValueError('Classfile path/name mismatch: '+name)
        if any(_references(root,internal) for root in client_roots):
            raise ValueError('Class referenced by installed client: '+name)
        if name in old and not _binary_compatible(_class_info(old[name]),after):
            raise ValueError('Binary shape changed; explicit compatibility review required: '+name)
    return changed

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--client',required=True,type=Path);parser.add_argument('--server',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    changed=compare(args.client,args.server)
    receipt={'client_sha256':digest(args.client),'server_sha256':digest(args.server),'changed_server_entries':changed,'preserve_hosted_client':True,'server_destination':'mods/psychiatryk_roles-3.0.0-bmc5.jar','hosted_client_destination':'automodpack/host-modpack/main/mods/psychiatryk_roles-3.0.0-bmc5.jar','required_checks':['native regression tests','server restart smoke','join with unchanged client','verify client feed hash unchanged']}
    args.output.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
