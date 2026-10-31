import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

/** Executes the actually transformed procedures against counting API stubs, NOT a Minecraft world. */
public final class Sandbox {
    public static Map<String, String> apiTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("test.Probe", "public class Probe { public static int lookups,plays,commands,tasks,discards,reads,draws; public static double[] rolls; public static String sound; public static void reset(double[] values) { lookups=plays=commands=tasks=discards=reads=draws=0; sound=null; rolls=values; } public static double random() { return rolls[draws++]; } }");
        types.put("net.minecraft.resources.ResourceLocation", "public class ResourceLocation { public final String id; public ResourceLocation(String id) { this.id=id; } }");
        types.put("net.minecraft.core.BlockPos", "public class BlockPos { public static BlockPos m_274561_(double x,double y,double z) { return new BlockPos(); } }");
        types.put("net.minecraft.core.Holder", "public interface Holder { default boolean m_203373_(net.minecraft.resources.ResourceLocation id) { return false; } }");
        types.put("net.minecraft.world.phys.Vec3", "public class Vec3 { public Vec3(double x,double y,double z) {} }");
        types.put("net.minecraft.world.phys.Vec2", "public class Vec2 { public static final Vec2 f_82462_=new Vec2(); }");
        types.put("net.minecraft.world.phys.AABB", "public class AABB { public static AABB m_165882_(Vec3 p,double x,double y,double z) { return new AABB(); } }");
        types.put("net.minecraft.world.level.LevelAccessor", "public interface LevelAccessor { default boolean m_5776_() { return false; } default boolean m_46861_(net.minecraft.core.BlockPos pos) { test.Probe.reads++; return false; } default net.minecraft.core.Holder m_204166_(net.minecraft.core.BlockPos pos) { test.Probe.reads++; return new net.minecraft.core.Holder() {}; } default java.util.List m_6443_(Class type,net.minecraft.world.phys.AABB box,java.util.function.Predicate predicate) { return java.util.List.of(new net.minecraft.world.entity.player.Player(new Level(false))); } }");
        types.put("net.minecraft.world.level.Level", "public class Level implements LevelAccessor { private final boolean client; public Level(boolean client) { this.client=client; } public boolean m_5776_() { return client; } public void m_5594_(net.minecraft.world.entity.player.Player p,net.minecraft.core.BlockPos pos,net.minecraft.sounds.SoundEvent sound,net.minecraft.sounds.SoundSource source,float volume,float pitch) { test.Probe.plays++; test.Probe.sound=sound.id; } public void m_7785_(double x,double y,double z,net.minecraft.sounds.SoundEvent sound,net.minecraft.sounds.SoundSource source,float volume,float pitch,boolean delay) { test.Probe.plays++; test.Probe.sound=sound.id; } }");
        types.put("net.minecraft.world.entity.Entity", "public class Entity { private final net.minecraft.world.level.Level level; public Entity(net.minecraft.world.level.Level level) { this.level=level; } public net.minecraft.world.level.Level m_9236_() { return level; } public void m_146870_() { test.Probe.discards++; } public double m_20186_() { return 0; } }");
        types.put("net.minecraft.world.entity.player.Player", "public class Player extends net.minecraft.world.entity.Entity { public Player(net.minecraft.world.level.Level level) { super(level); } }");
        types.put("net.mcreator.interpritation.network.ThebrokenscriptModVariables", "public class ThebrokenscriptModVariables { public static class MapVariables { public boolean iscircuitspawned,isvoidspawned; private static final MapVariables INSTANCE=new MapVariables(); public static MapVariables get(net.minecraft.world.level.LevelAccessor level) { test.Probe.reads++; return INSTANCE; } public void syncData(net.minecraft.world.level.LevelAccessor level) {} public static void reset() { INSTANCE.iscircuitspawned=INSTANCE.isvoidspawned=false; } } }");
        types.put("net.mcreator.interpritation.ThebrokenscriptMod", "public class ThebrokenscriptMod { public static void queueServerWork(int ticks,Runnable work) { test.Probe.tasks++; } }");
        types.put("net.minecraft.sounds.SoundEvent", "public class SoundEvent { public final String id; public SoundEvent(String id) { this.id=id; } }");
        types.put("net.minecraft.sounds.SoundSource", "public enum SoundSource { NEUTRAL }");
        types.put("net.minecraftforge.registries.IForgeRegistry", "public interface IForgeRegistry { Object getValue(net.minecraft.resources.ResourceLocation id); }");
        types.put("net.minecraftforge.registries.ForgeRegistries", "public class ForgeRegistries { public static final IForgeRegistry SOUND_EVENTS=id -> { test.Probe.lookups++; return new net.minecraft.sounds.SoundEvent(id.id); }; }");
        types.put("org.slf4j.Logger", "public interface Logger { void info(String text); }");
        types.put("com.mojang.logging.LogUtils", "public class LogUtils { public static org.slf4j.Logger getLogger() { return text -> {}; } }");
        types.put("net.minecraft.commands.CommandSource", "public interface CommandSource { CommandSource f_80164_=new CommandSource() {}; }");
        types.put("net.minecraft.network.chat.Component", "public interface Component { static MutableComponent m_237113_(String text) { return new MutableComponent(); } }");
        types.put("net.minecraft.network.chat.MutableComponent", "public class MutableComponent implements Component {}");
        types.put("net.minecraft.commands.CommandSourceStack", "public class CommandSourceStack { public CommandSourceStack(CommandSource source,net.minecraft.world.phys.Vec3 pos,net.minecraft.world.phys.Vec2 rot,net.minecraft.server.level.ServerLevel level,int permission,String name,net.minecraft.network.chat.Component display,net.minecraft.server.MinecraftServer server,net.minecraft.world.entity.Entity entity) {} public CommandSourceStack m_81324_() { return this; } }");
        types.put("net.minecraft.commands.Commands", "public class Commands { public int m_230957_(CommandSourceStack source,String text) { test.Probe.commands++; return 0; } }");
        types.put("net.minecraft.server.MinecraftServer", "public class MinecraftServer { public net.minecraft.commands.Commands m_129892_() { return new net.minecraft.commands.Commands(); } }");
        types.put("net.minecraft.server.level.ServerLevel", "public class ServerLevel extends net.minecraft.world.level.Level { public ServerLevel() { super(false); } public net.minecraft.server.MinecraftServer m_7654_() { return new net.minecraft.server.MinecraftServer(); } }");
        types.put("net.minecraft.util.RandomSource", "public interface RandomSource { static RandomSource m_216327_() { return new RandomSource() {}; } }");
        types.put("net.minecraft.util.Mth", "public class Mth { public static int m_216271_(RandomSource random,int min,int max) { return min; } }");
        return types;
    }

    public static Path compileStubs(Path root, Map<String, String> types) throws Exception {
        Path source = root.resolve("stub-src"), classes = root.resolve("stub-classes");
        Files.createDirectories(classes);
        List<String> compilerArgs = new ArrayList<>(List.of("-proc:none", "-d", classes.toString()));
        for (var entry : types.entrySet()) {
            int separator = entry.getKey().lastIndexOf('.');
            Path file = source.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + entry.getKey().substring(0, separator) + ";\n" + entry.getValue());
            compilerArgs.add(file.toString());
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, compilerArgs.toArray(String[]::new)) != 0) throw new AssertionError("Stub compilation failed");
        return classes;
    }

    public static void run(Path transformed, Path rus, Path mixin) throws Exception {
        Path classes = compileStubs(transformed, apiTypes());
        Path executable = transformed.resolve("executable");
        for (String simple : List.of("CircuitStareOnInitialEntitySpawnProcedure", "VoiddOnInitialEntitySpawnProcedure")) {
            String internal = "net/mcreator/interpritation/procedures/" + simple;
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(transformed.resolve(internal + ".class"))).accept(node, 0);
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                // Deterministic RNG only. All transformed callbacks/branches remain actual Mixin output.
                if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/Math") && call.name.equals("random")) call.owner = "test/Probe";
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            Path file = executable.resolve(internal + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, writer.toByteArray());
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{executable.toUri().toURL(), classes.toUri().toURL(), rus.toUri().toURL(), mixin.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> probe = loader.loadClass("test.Probe");
            Class<?> accessor = loader.loadClass("net.minecraft.world.level.LevelAccessor");
            Class<?> levelType = loader.loadClass("net.minecraft.world.level.Level");
            Class<?> entityType = loader.loadClass("net.minecraft.world.entity.Entity");
            Class<?> vars = loader.loadClass("net.mcreator.interpritation.network.ThebrokenscriptModVariables$MapVariables");
            var instanceField = vars.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object flags = instanceField.get(null);
            int cases = 0;
            for (boolean client : new boolean[]{false, true}) {
                Object level = client ? levelType.getConstructor(boolean.class).newInstance(true)
                        : loader.loadClass("net.minecraft.server.level.ServerLevel").getConstructor().newInstance();
                Object entity = entityType.getConstructor(levelType).newInstance(level);
                var circuit = loader.loadClass("net.mcreator.interpritation.procedures.CircuitStareOnInitialEntitySpawnProcedure")
                        .getMethod("execute", accessor, double.class, double.class, double.class, entityType);
                var voidEvent = loader.loadClass("net.mcreator.interpritation.procedures.VoiddOnInitialEntitySpawnProcedure")
                        .getMethod("execute", accessor, double.class, double.class, double.class, entityType);
                for (int repeat = 0; repeat < 20; repeat++) {
                    probe.getMethod("reset", double[].class).invoke(null, (Object) new double[]{0.0, 0.0});
                    vars.getMethod("reset").invoke(null);
                    circuit.invoke(null, level, 0.0, 0.0, 0.0, entity);
                    noSubmission(probe);
                    check(number(probe, "discards") == (client ? 0 : 1), "Circuit rejected entity cleanup");
                    check(!vars.getField("iscircuitspawned").getBoolean(flags), "cancelled Circuit activated its lock");
                    check(number(probe, "draws") == 2, "Circuit selection rolls changed");
                    cases++;
                    probe.getMethod("reset", double[].class).invoke(null, (Object) new double[]{0.0});
                    vars.getMethod("reset").invoke(null);
                    voidEvent.invoke(null, level, 0.0, 0.0, 0.0, entity);
                    noSubmission(probe);
                    check(number(probe, "reads") == 0 && number(probe, "draws") == 0, "Void original initializer executed");
                    check(!vars.getField("isvoidspawned").getBoolean(flags), "cancelled Void activated its lock");
                    cases++;
                }
                for (double choice : new double[]{0.7, 0.9}) {
                    probe.getMethod("reset", double[].class).invoke(null, (Object) new double[]{0.0, choice});
                    vars.getMethod("reset").invoke(null);
                    circuit.invoke(null, level, 0.0, 0.0, 0.0, entity);
                    check(number(probe, "lookups") == 1 && number(probe, "plays") == 1 && number(probe, "tasks") == 1,
                            "integritywatching path changed");
                    check("thebrokenscript:integritywatching".equals(probe.getField("sound").get(null)), "wrong alternative track");
                    cases++;
                }
            }
            System.out.println("PASS: " + cases + " JVM executions of actual Mixin-transformed procedures with counting API stubs: zero falsecalm2 lookups/play submissions/commands/tasks; integritywatching preserved.");
        }
    }
    private static int number(Class<?> probe, String field) throws Exception { return probe.getField(field).getInt(null); }
    private static void noSubmission(Class<?> probe) throws Exception {
        for (String field : List.of("lookups", "plays", "commands", "tasks")) check(number(probe, field) == 0, "forbidden submission: " + field);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
