package pl.aridlin.psychiatrykroles.music;
import java.util.ArrayList;
import java.util.List;
public final class BackgroundPauseTest {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        var state = new BackgroundMusicPauseState<Object, Object>();
        Object music = new Object(), channel = new Object(), other = new Object(), replacement = new Object();
        List<Object> paused = new ArrayList<>(), resumed = new ArrayList<>();
        check(!state.update(false,false,false,music,channel,paused::add,resumed::add),"idle scheduler untouched");
        check(paused.isEmpty() && resumed.isEmpty(),"idle channel untouched");
        check(state.update(true,false,false,music,null,paused::add,resumed::add),"no new background while its channel prepares");
        check(paused.isEmpty(),"no channel before creation");
        check(state.update(true,false,false,music,channel,paused::add,resumed::add),"audible jukebox pauses scheduler");
        check(paused.equals(List.of(channel)),"only own music channel paused");
        check(state.update(true,false,false,music,channel,paused::add,resumed::add),"reassert after menu resumes all sounds");
        check(paused.size()==2 && resumed.isEmpty(),"no premature unpause");
        for (int tick=0; tick<20; tick++) check(state.update(false,true,false,music,channel,paused::add,resumed::add),"one-second playlist handover "+tick);
        check(!state.update(false,true,false,music,channel,paused::add,resumed::add),"failed/unready import cannot mute music forever");
        check(resumed.equals(List.of(channel)),"resume same channel, retain playback position");
        check(!state.update(false,false,false,music,channel,paused::add,resumed::add) && resumed.size()==1,"resume once");
        state.update(true,false,false,music,channel,paused::add,resumed::add);
        check(!state.update(false,false,false,music,channel,paused::add,resumed::add),"explicit stop/pause, no lingering handover");
        check(resumed.size()==2,"immediate resume when no preparation");
        state.update(true,false,false,music,channel,paused::add,resumed::add);
        check(!state.update(false,false,true,music,channel,paused::add,resumed::add),"paused local menu releases scheduler normally");
        check(resumed.size()==2,"never unpause sounds behind paused game menu");
        state.update(false,false,false,music,channel,paused::add,resumed::add);
        check(resumed.size()==3,"resume after game menu closes");
        state.update(true,false,false,music,channel,paused::add,resumed::add);
        state.update(false,false,false,other,replacement,paused::add,resumed::add);
        check(resumed.size()==3,"music changed: cannot revive old dimension track");
        state.update(true,false,false,music,channel,paused::add,resumed::add);
        state.update(false,false,false,music,replacement,paused::add,resumed::add);
        check(resumed.size()==3,"device/resource reload: cannot unpause released channel");
        state.update(true,false,false,music,channel,paused::add,resumed::add);
        state.update(false,false,false,null,null,paused::add,resumed::add);
        check(resumed.size()==3,"manager stop does not revive deleted music");
        var listener2 = new BackgroundMusicPauseState<Object,Object>();
        check(!listener2.update(false,false,false,other,replacement,paused::add,resumed::add),"per-listener state has no cross-player suppression");
        check(MusicAudibility.audible(63.9*63.9,1,1,1,64,false,true),"inside radius");
        check(!MusicAudibility.audible(64*64,1,1,1,64,false,true),"at silent edge");
        check(!MusicAudibility.audible(65*65,1,1,1,64,false,true),"out of range");
        check(MusicAudibility.audible(63.9*63.9,4,1,1,16,false,true),"vanilla disc volume-four range matches engine");
        check(!MusicAudibility.audible(1,0,1,1,64,false,true),"source slider zero");
        check(!MusicAudibility.audible(1,1,0,1,64,false,true),"category muted");
        check(!MusicAudibility.audible(1,1,1,0,64,false,true),"master muted");
        check(!MusicAudibility.audible(1,Float.NaN,1,1,64,false,true),"invalid volume cannot suppress");
        check(!MusicAudibility.audible(Double.NaN,1,1,1,64,false,true),"invalid position cannot suppress");
        check(MusicAudibility.audible(1e10,1,1,1,64,true,true),"relative record obeys native attenuation rule");
        check(MusicAudibility.audible(1e10,1,1,1,64,false,false),"nonattenuated record obeys native rule");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_music_channel_pause_state_verified\":true,\"scheduler_and_lifecycle_verified\":true,\"audibility_verified\":true,\"game_launched\":false}");
    }
}
