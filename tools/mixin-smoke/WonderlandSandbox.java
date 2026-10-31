import org.objectweb.asm.tree.*;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

/** Counting-API tests for the real transformed entry methods and the real compiled packet helper. */
public final class WonderlandSandbox {
    public static void run(Path transformed, Path wonderland, Path mod, Set<String> registeredDimensions) throws Exception {
        Map<String, String> types = Sandbox.apiTypes();
        types.put("net.minecraft.resources.ResourceLocation", "public class ResourceLocation { public final String id; public ResourceLocation(String id) { this.id=id; } public String toString() { return id; } }");
        types.put("net.minecraft.resources.ResourceKey", "public class ResourceKey { private final ResourceLocation id; public ResourceKey(String id) { this.id=new ResourceLocation(id); } public ResourceLocation m_135782_() { return id; } }");
        types.put("net.minecraft.world.level.Level", "public class Level implements LevelAccessor { private final net.minecraft.resources.ResourceKey key; public Level(boolean client) { this(\"minecraft:overworld\"); } public Level(String id) { key=new net.minecraft.resources.ResourceKey(id); } public boolean m_5776_() { return false; } public net.minecraft.resources.ResourceKey m_46472_() { return key; } }");
        types.put("net.minecraft.server.level.ServerLevel", "public class ServerLevel extends net.minecraft.world.level.Level { public ServerLevel(String id) { super(id); } public net.minecraft.server.MinecraftServer m_7654_() { return new net.minecraft.server.MinecraftServer(); } }");
        types.put("test.EntryProbe", "public class EntryProbe { public static int moves,effects; public static void reset() { moves=effects=0; } }");
        types.put("net.minecraft.world.entity.Entity", "public class Entity { private final net.minecraft.world.level.Level level; public Entity(net.minecraft.world.level.Level level) { this.level=level; } public net.minecraft.world.level.Level m_9236_() { return level; } public void m_6021_(double x,double y,double z) { test.EntryProbe.moves++; } public float m_146908_() { return 0; } public float m_146909_() { return 0; } }");
        types.put("net.minecraft.world.entity.LivingEntity", "public class LivingEntity extends Entity { public LivingEntity(net.minecraft.world.level.Level level) { super(level); } public boolean m_7292_(net.minecraft.world.effect.MobEffectInstance effect) { test.EntryProbe.effects++; return true; } }");
        types.put("net.minecraft.world.effect.MobEffect", "public class MobEffect {}");
        types.put("net.minecraft.world.effect.MobEffectInstance", "public class MobEffectInstance { public MobEffectInstance(MobEffect effect,int duration,int strength,boolean ambient,boolean visible) {} }");
        Set<String> effectFields = new TreeSet<>();
        Map<String, String> globalFields = new TreeMap<>(), mapFields = new TreeMap<>();
        for (String simple : WonderlandSmoke.ENTRIES) {
            ClassNode original = WonderlandSmoke.node(WonderlandSmoke.resource(WonderlandSmoke.PREFIX + simple + "Procedure"));
            for (MethodNode method : original.methods) for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/world/effect/MobEffects")) effectFields.add(field.name);
                if (instruction instanceof FieldInsnNode field && field.owner.equals("net/mcreator/thewonderland/network/TheWonderlandModVariables")) globalFields.put(field.name, field.desc);
                if (instruction instanceof FieldInsnNode field && field.owner.equals("net/mcreator/thewonderland/network/TheWonderlandModVariables$MapVariables")) mapFields.put(field.name, field.desc);
            }
        }
        StringBuilder fields = new StringBuilder("public class MobEffects {");
        for (String name : effectFields) fields.append(" public static final MobEffect ").append(name).append("=new MobEffect();");
        types.put("net.minecraft.world.effect.MobEffects", fields + " }");
        types.put("net.minecraftforge.registries.RegistryObject", "public class RegistryObject { public Object get() { return new net.minecraft.world.effect.MobEffect(); } }");
        types.put("net.mcreator.thewonderland.init.TheWonderlandModMobEffects", "public class TheWonderlandModMobEffects { public static final net.minecraftforge.registries.RegistryObject DREAMWORLD_TIMER=new net.minecraftforge.registries.RegistryObject(); }");
        StringBuilder variables = new StringBuilder("public class TheWonderlandModVariables {");
        for (var field : globalFields.entrySet()) variables.append("public static ").append(javaType(field.getValue())).append(' ').append(field.getKey()).append(';');
        variables.append("public static class MapVariables {");
        for (var field : mapFields.entrySet()) variables.append("public ").append(javaType(field.getValue())).append(' ').append(field.getKey()).append(';');
        variables.append("private static final MapVariables INSTANCE=new MapVariables(); public static MapVariables get(net.minecraft.world.level.LevelAccessor level) { return INSTANCE; } public void syncData(net.minecraft.world.level.LevelAccessor level) {} } }");
        types.put("net.mcreator.thewonderland.network.TheWonderlandModVariables", variables.toString());
        types.put("net.mcreator.thewonderland.TheWonderlandMod", "public class TheWonderlandMod { public static void queueServerWork(int ticks,Runnable work) { if(ticks!=1) throw new AssertionError(\"Late cleanup still queued: \"+ticks); test.Probe.tasks++; work.run(); } }");
        types.put("net.minecraft.network.protocol.Packet", "public interface Packet {}");
        types.put("net.minecraft.network.protocol.game.ClientboundStopSoundPacket", "public class ClientboundStopSoundPacket implements net.minecraft.network.protocol.Packet { public final net.minecraft.resources.ResourceLocation name; public final net.minecraft.sounds.SoundSource source; public ClientboundStopSoundPacket(net.minecraft.resources.ResourceLocation name,net.minecraft.sounds.SoundSource source) { this.name=name; this.source=source; } }");
        types.put("net.minecraft.server.network.ServerGamePacketListenerImpl", "public class ServerGamePacketListenerImpl { public final java.util.List<net.minecraft.network.protocol.Packet> packets=new java.util.ArrayList<>(); public void m_9829_(net.minecraft.network.protocol.Packet packet) { packets.add(packet); } public void m_9774_(double x,double y,double z,float yaw,float pitch) {} }");
        types.put("net.minecraft.server.level.ServerPlayer", "public class ServerPlayer extends net.minecraft.world.entity.LivingEntity { public final net.minecraft.server.network.ServerGamePacketListenerImpl f_8906_=new net.minecraft.server.network.ServerGamePacketListenerImpl(); public ServerPlayer(net.minecraft.world.level.Level level) { super(level); } }");
        types.put("net.minecraftforge.fml.ModList", "public class ModList { public static boolean loaded=true; public static ModList get() { return new ModList(); } public boolean isLoaded(String id) { return loaded; } }");
        Path classes = Sandbox.compileStubs(transformed, types);
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(), transformed.toUri().toURL(), mod.toUri().toURL(), wonderland.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> helper = loader.loadClass("com.jonathan.chatoverhaul.event.WonderlandEntryAudio");
            Class<?> level = loader.loadClass("net.minecraft.world.level.Level");
            Class<?> serverLevel = loader.loadClass("net.minecraft.server.level.ServerLevel");
            Class<?> player = loader.loadClass("net.minecraft.server.level.ServerPlayer");
            Class<?> entity = loader.loadClass("net.minecraft.world.entity.Entity");
            Class<?> accessor = loader.loadClass("net.minecraft.world.level.LevelAccessor");
            Class<?> probe = loader.loadClass("test.Probe");
            Class<?> entryProbe = loader.loadClass("test.EntryProbe");
            var policy = helper.getMethod("needsCleanup", boolean.class, String.class, String.class);
            var cleanup = helper.getMethod("beforeRespawn", player, serverLevel);
            var destinations = helper.getDeclaredField("DESTINATIONS"); destinations.setAccessible(true);
            WonderlandSmoke.check(destinations.get(null).equals(registeredDimensions), "policy differs from actual dimension registrations");
            Object oldLevel = serverLevel.getConstructor(String.class).newInstance("minecraft:overworld");
            Object bystander = player.getConstructor(level).newInstance(oldLevel);
            int cases = 0;
            for (String destination : registeredDimensions) {
                WonderlandSmoke.check((boolean) policy.invoke(null, true, "minecraft:overworld", destination), "missing destination " + destination);
                WonderlandSmoke.check(!(boolean) policy.invoke(null, true, destination, destination), "same-world teleport must not clear audio");
                WonderlandSmoke.check(!(boolean) policy.invoke(null, false, "minecraft:overworld", destination), "absent mod must not clear audio");
                Object traveler = player.getConstructor(level).newInstance(oldLevel);
                Object next = serverLevel.getConstructor(String.class).newInstance(destination);
                cleanup.invoke(null, traveler, next);
                List<?> packets = packets(player, traveler);
                WonderlandSmoke.check(packets.size() == 1 && packets.get(0).getClass().getSimpleName().equals("ClientboundStopSoundPacket"), "expected exactly one old-scene cleanup");
                WonderlandSmoke.check(packets(player, bystander).isEmpty(), "bystander lost audio");
                cases++;
            }
            for (String other : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "the_wonderland:bridges", "the_wonderland:reservoir", "thebrokenscript:outside")) {
                WonderlandSmoke.check(!(boolean) policy.invoke(null, true, "the_wonderland:depths", other), "unrelated transition changed " + other);
                Object traveler = player.getConstructor(level).newInstance(oldLevel);
                cleanup.invoke(null, traveler, serverLevel.getConstructor(String.class).newInstance(other));
                WonderlandSmoke.check(packets(player, traveler).isEmpty(), "unrelated player received stop packet");
                cases++;
            }
            for (String simple : WonderlandSmoke.ENTRIES) {
                probe.getMethod("reset", double[].class).invoke(null, (Object) new double[0]);
                entryProbe.getMethod("reset").invoke(null);
                Object destination = serverLevel.getConstructor(String.class).newInstance("the_wonderland:depths");
                Object traveler = player.getConstructor(level).newInstance(destination);
                var execute = loader.loadClass((WonderlandSmoke.PREFIX + simple + "Procedure").replace('/', '.'))
                        .getMethod("execute", accessor, double.class, double.class, double.class, entity);
                execute.invoke(null, destination, 12.0, 8.0, 14.0, traveler);
                int tasks = probe.getField("tasks").getInt(null);
                WonderlandSmoke.check(tasks == (simple.equals("TunnelsEnter") || simple.equals("WhatOnceWasEnter") ? 1 : 0), "teleport scheduling changed: " + simple);
                WonderlandSmoke.check(probe.getField("commands").getInt(null) == 0 && packets(player, traveler).isEmpty(), "entry still clears new-scene audio: " + simple);
                WonderlandSmoke.check(entryProbe.getField("moves").getInt(null) > 0 && entryProbe.getField("effects").getInt(null) > 0, "entry gameplay skipped: " + simple);
                cases++;
            }
            System.out.println("PASS: " + cases + " isolated JVM cases: 14 registered destinations, unrelated/same-world/absent-mod policy, targeted packets without bystander stops, and 12 actually transformed entry methods preserve movement/effects with no late stop task.");
        }
    }
    private static List<?> packets(Class<?> player, Object instance) throws Exception {
        Object connection = player.getField("f_8906_").get(instance);
        return (List<?>) connection.getClass().getField("packets").get(connection);
    }
    private static String javaType(String descriptor) {
        return switch (descriptor) { case "Z" -> "boolean"; case "D" -> "double"; case "I" -> "int"; default -> throw new AssertionError("Unsupported state field " + descriptor); };
    }
}
