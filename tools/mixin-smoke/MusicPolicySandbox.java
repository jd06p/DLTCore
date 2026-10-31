import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

/** Isolated execution of the actual native/transformed MusicManagers; no game world or audio device. */
public final class MusicPolicySandbox {
    private static final Set<String> ALLOWED = Set.of("wonderland", "false_wonderland", "sarcophagus", "no_mans_land",
            "what_once_was", "accountability", "endless_field", "compliance", "dlrowmaerd", "corners", "maze");

    public static void run(Path transformed, Path mod, Path minecraft) throws Exception {
        Map<String, String> types = Sandbox.apiTypes();
        types.put("test.MusicProbe", "public class MusicProbe { public static int created,plays,stops,random; public static void reset() { created=plays=stops=random=0; } }");
        types.put("net.minecraft.resources.ResourceLocation", "public class ResourceLocation { private final String namespace,path; public ResourceLocation(String id) { String[] p=id.split(\":\",2); namespace=p.length==2?p[0]:\"minecraft\"; path=p.length==2?p[1]:p[0]; } public ResourceLocation(String ns,String p) { namespace=ns; path=p; } public String m_135827_() { return namespace; } public String m_135815_() { return path; } public String toString() { return namespace+\":\"+path; } public boolean equals(Object o) { return o instanceof ResourceLocation r && toString().equals(r.toString()); } public int hashCode() { return toString().hashCode(); } }");
        types.put("net.minecraft.resources.ResourceKey", "public class ResourceKey { private final ResourceLocation id; public ResourceKey(String id) { this.id=new ResourceLocation(id); } public ResourceLocation m_135782_() { return id; } }");
        types.put("net.minecraft.world.level.Level", "public class Level { private final net.minecraft.resources.ResourceKey key; public Level(String id) { key=new net.minecraft.resources.ResourceKey(id); } public net.minecraft.resources.ResourceKey m_46472_() { return key; } }");
        // Remove irrelevant base world/entity stubs whose constructors assume Level(boolean).
        for (String name : List.of("net.minecraft.world.level.LevelAccessor", "net.minecraft.world.entity.Entity", "net.minecraft.world.entity.player.Player", "net.mcreator.interpritation.network.ThebrokenscriptModVariables", "net.minecraft.server.level.ServerLevel", "net.minecraft.commands.CommandSourceStack", "net.minecraft.commands.Commands", "net.minecraft.server.MinecraftServer")) types.remove(name);
        types.put("net.minecraft.client.multiplayer.ClientLevel", "public class ClientLevel extends net.minecraft.world.level.Level { public ClientLevel(String id) { super(id); } }");
        types.put("net.minecraft.core.Holder", "public interface Holder { Object m_203334_(); }");
        types.put("net.minecraft.sounds.SoundEvent", "public class SoundEvent { private final net.minecraft.resources.ResourceLocation id; public SoundEvent(String id) { this.id=new net.minecraft.resources.ResourceLocation(id); } public net.minecraft.resources.ResourceLocation m_11660_() { return id; } }");
        types.put("net.minecraft.sounds.Music", "public class Music { private final SoundEvent event; private final int min,max; private final boolean replace; public Music(String id,int min,int max,boolean replace) { event=new SoundEvent(id); this.min=min; this.max=max; this.replace=replace; } public net.minecraft.core.Holder m_263193_() { return () -> event; } public int m_11636_() { return min; } public int m_11639_() { return max; } public boolean m_11642_() { return replace; } }");
        types.put("net.minecraft.util.Mth", "public class Mth { public static int m_216271_(RandomSource random,int min,int max) { test.MusicProbe.random++; return min; } }");
        types.put("net.minecraft.client.resources.sounds.Sound", "public class Sound {}");
        types.put("net.minecraft.client.resources.sounds.SoundInstance", "public interface SoundInstance { net.minecraft.resources.ResourceLocation m_7904_(); Sound m_5891_(); }");
        types.put("net.minecraft.client.resources.sounds.SimpleSoundInstance", "public class SimpleSoundInstance implements SoundInstance { private final net.minecraft.sounds.SoundEvent event; public SimpleSoundInstance(net.minecraft.sounds.SoundEvent event) { this.event=event; } public static SimpleSoundInstance m_119745_(net.minecraft.sounds.SoundEvent event) { test.MusicProbe.created++; return new SimpleSoundInstance(event); } public net.minecraft.resources.ResourceLocation m_7904_() { return event.m_11660_(); } public Sound m_5891_() { return new Sound(); } }");
        types.put("net.minecraft.client.sounds.SoundManager", "public class SoundManager { public static final net.minecraft.client.resources.sounds.Sound f_120344_=new net.minecraft.client.resources.sounds.Sound(); private final java.util.Set<net.minecraft.client.resources.sounds.SoundInstance> active=new java.util.HashSet<>(); public void m_120367_(net.minecraft.client.resources.sounds.SoundInstance instance) { test.MusicProbe.plays++; active.add(instance); } public void m_120399_(net.minecraft.client.resources.sounds.SoundInstance instance) { test.MusicProbe.stops++; active.remove(instance); } public boolean m_120403_(net.minecraft.client.resources.sounds.SoundInstance instance) { return active.contains(instance); } public void finishAll() { active.clear(); } }");
        types.put("net.minecraft.client.Minecraft", "public class Minecraft { public net.minecraft.client.multiplayer.ClientLevel f_91073_; public net.minecraft.sounds.Music selected; public int selections; private final net.minecraft.client.sounds.SoundManager manager=new net.minecraft.client.sounds.SoundManager(); public net.minecraft.sounds.Music m_91107_() { selections++; return selected; } public net.minecraft.client.sounds.SoundManager m_91106_() { return manager; } }");
        // Base stubs for ForgeRegistries expect SoundEvent.id; MusicManager does not use registries.
        types.remove("net.minecraftforge.registries.ForgeRegistries");
        Path classes = Sandbox.compileStubs(transformed, types);
        Path mixin = Path.of(org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var patchedLoader = loader(classes, transformed, mod, minecraft, mixin);
             var nativeLoader = loader(classes, null, mod, minecraft, mixin)) {
            Class<?> policy = patchedLoader.loadClass("com.jonathan.chatoverhaul.client.WonderlandVanillaMusicPolicy");
            Class<?> resource = patchedLoader.loadClass("net.minecraft.resources.ResourceLocation");
            var block = policy.getMethod("shouldBlock", resource, resource);
            Set<?> whitelist = (Set<?>) policy.getField("VANILLA_MUSIC_ALLOWED_DIMENSIONS").get(null);
            Set<String> expected = new HashSet<>(); for (String s : ALLOWED) expected.add("the_wonderland:" + s);
            Set<String> actual = new HashSet<>(); for (Object r : whitelist) actual.add(r.toString());
            check(actual.equals(expected), "exception list must match all eleven requested IDs exactly");
            int cases = 0;
            List<String> vanilla = List.of("minecraft:music.game", "minecraft:music.creative", "minecraft:music.under_water", "minecraft:music.overworld.meadow", "minecraft:music.nether.warped_forest");
            for (String dimension : expected) for (String sound : vanilla) {
                Fixture a = new Fixture(patchedLoader, dimension, sound), b = new Fixture(nativeLoader, dimension, sound);
                for (int tick = 0; tick < 15; tick++) {
                    if (tick == 4 || tick == 10) { a.finish(); b.finish(); }
                    a.tick(); b.tick();
                    check(a.snapshot().equals(b.snapshot()), "allowed music diverged from native manager: " + dimension + " " + sound);
                }
                cases++;
            }
            for (String dimension : List.of("the_wonderland:depths", "the_wonderland:the_slip", "the_wonderland:acceptance", "the_wonderland:reservoir", "the_wonderland:future_dimension")) for (String sound : vanilla) {
                Fixture f = new Fixture(patchedLoader, dimension, sound);
                for (int tick = 0; tick < 2000; tick++) f.tick();
                check(f.count("created") == 0 && f.count("plays") == 0 && f.count("stops") == 0 && f.current() == null, "blocked vanilla entered sound system");
                check(f.delay() == 0, "blocked timer must remain ready, not become a fake long cooldown");
                check(f.selections() == 2000, "situational music must not be queried twice per tick");
                f.delay(37); f.start(sound);
                check(f.current() == null && f.count("created") == 0 && f.delay() == 37, "direct blocked start changed music state");
                cases++;
            }
            for (String dimension : List.of("the_wonderland:depths", "minecraft:overworld", "minecraft:the_nether", "other_mod:depths", "the_wonderlandish:depths")) {
                for (String sound : List.of("the_wonderland:depth", "the_wonderland:what_once_was_ambience", "other_mod:music.custom", "thebrokenscript:moonglitch", "minecraft:ambient.cave", "minecraft:music_disc.13")) {
                    Fixture a = new Fixture(patchedLoader, dimension, sound), b = new Fixture(nativeLoader, dimension, sound);
                    a.tick(); b.tick(); check(a.snapshot().equals(b.snapshot()), "unrelated custom audio changed"); cases++;
                }
                if (!dimension.equals("the_wonderland:depths")) {
                    Fixture a = new Fixture(patchedLoader, dimension, vanilla.get(0)), b = new Fixture(nativeLoader, dimension, vanilla.get(0));
                    a.tick(); b.tick(); check(a.snapshot().equals(b.snapshot()), "outside dimension vanilla audio changed"); cases++;
                }
            }
            Fixture f = new Fixture(patchedLoader, "the_wonderland:depths", "the_wonderland:custom_background");
            f.start("the_wonderland:custom_background"); Object custom = f.current();
            f.select("minecraft:music.game", true);
            for (int i = 0; i < 100; i++) f.tick();
            check(f.current() == custom && f.count("stops") == 0 && f.count("plays") == 1, "blocked replace-current selection interrupted custom track");
            f.finish(); f.tick(); check(f.current() == null, "completed custom track left stale currentMusic");
            f.select("the_wonderland:custom_background", true);
            for (int i = 0; i < 10; i++) f.tick();
            check(f.current() != null && f.count("plays") == 2, "custom selection cannot resume after rejection"); cases++;
            Fixture travel = new Fixture(patchedLoader, "the_wonderland:wonderland", "minecraft:music.game");
            travel.tick(); check(travel.count("plays") == 1, "allowed initial background missing");
            travel.transition("the_wonderland:depths"); travel.resetCounts();
            for (int i = 0; i < 300; i++) travel.tick();
            check(travel.count("plays") == 0 && travel.count("stops") == 0 && travel.current() == null, "restricted transition submitted or repeatedly stopped vanilla music");
            travel.transition("the_wonderland:reservoir");
            for (int i = 0; i < 150; i++) travel.tick();
            check(travel.count("plays") == 0, "restricted-to-restricted policy failed");
            travel.transition("the_wonderland:maze");
            for (int i = 0; i < 150; i++) travel.tick();
            check(travel.count("plays") == 1, "native allowed timing failed after leaving restriction");
            travel.transition("minecraft:overworld");
            for (int i = 0; i < 150; i++) travel.tick();
            check(travel.count("plays") == 2, "outside-world native music did not resume"); cases++;
            Fixture menu = new Fixture(patchedLoader, null, "minecraft:music.menu"), nativeMenu = new Fixture(nativeLoader, null, "minecraft:music.menu");
            menu.tick(); nativeMenu.tick(); check(menu.snapshot().equals(nativeMenu.snapshot()), "title menu music affected"); cases++;
            Object vanillaId = resource.getConstructor(String.class).newInstance("minecraft:music.game");
            check(!(boolean) block.invoke(null, null, vanillaId), "null world must be allowed");
            check(!(boolean) block.invoke(null, resource.getConstructor(String.class).newInstance("the_wonderland:depths"), null), "null ID must not match");
            System.out.println("PASS: " + cases + " isolated policy/lifecycle cases using real native/transformed MusicManager: exact 11-dimension whitelist; no blocked SoundInstance creation/submission; allowed baseline behavior, custom-track preservation, stale-state cleanup, transitions and menu verified.");
        }
    }
    private static URLClassLoader loader(Path stubs, Path transformed, Path mod, Path game, Path mixin) throws Exception {
        List<java.net.URL> urls = new ArrayList<>(); urls.add(stubs.toUri().toURL());
        if (transformed != null) urls.add(transformed.toUri().toURL());
        for (Path p : List.of(mod, game, mixin)) urls.add(p.toUri().toURL());
        return new URLClassLoader(urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader());
    }
    private static final class Fixture {
        final ClassLoader loader; final Object mc, manager, soundManager; final Class<?> musicType, managerType, probe;
        Fixture(ClassLoader loader, String dimension, String selected) throws Exception {
            this.loader=loader;
            Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft"); mc=minecraft.getConstructor().newInstance();
            musicType=loader.loadClass("net.minecraft.sounds.Music"); managerType=loader.loadClass("net.minecraft.client.sounds.MusicManager");
            probe=loader.loadClass("test.MusicProbe"); manager=managerType.getConstructor(minecraft).newInstance(mc);
            soundManager=minecraft.getMethod("m_91106_").invoke(mc); world(dimension); select(selected, true); delay(0); resetCounts();
        }
        void world(String id) throws Exception { mc.getClass().getField("f_91073_").set(mc, id == null ? null : loader.loadClass("net.minecraft.client.multiplayer.ClientLevel").getConstructor(String.class).newInstance(id)); }
        Object music(String id, boolean replace) throws Exception { return musicType.getConstructor(String.class,int.class,int.class,boolean.class).newInstance(id,2,5,replace); }
        void select(String id, boolean replace) throws Exception { mc.getClass().getField("selected").set(mc,music(id,replace)); }
        void tick() throws Exception { managerType.getMethod("m_120183_").invoke(manager); }
        void start(String id) throws Exception { managerType.getMethod("m_120184_",musicType).invoke(manager,music(id,true)); }
        void finish() throws Exception { soundManager.getClass().getMethod("finishAll").invoke(soundManager); }
        Object current() throws Exception { var f=managerType.getDeclaredField("f_120179_"); f.setAccessible(true); return f.get(manager); }
        int delay() throws Exception { var f=managerType.getDeclaredField("f_120180_"); f.setAccessible(true); return f.getInt(manager); }
        void delay(int value) throws Exception { var f=managerType.getDeclaredField("f_120180_"); f.setAccessible(true); f.setInt(manager,value); }
        int count(String field) throws Exception { return probe.getField(field).getInt(null); }
        int selections() throws Exception { return mc.getClass().getField("selections").getInt(mc); }
        void resetCounts() throws Exception { probe.getMethod("reset").invoke(null); }
        void transition(String id) throws Exception { world(id); managerType.getMethod("m_120186_").invoke(manager); } // Native handleRespawn behavior.
        String snapshot() throws Exception {
            Object current=current(); String id=current==null?"null":current.getClass().getMethod("m_7904_").invoke(current).toString();
            return id+":"+delay()+":"+count("created")+":"+count("plays")+":"+count("stops")+":"+count("random")+":"+selections();
        }
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
