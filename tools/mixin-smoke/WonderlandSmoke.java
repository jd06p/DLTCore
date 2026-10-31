import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Real Mixin transformation + jar/packet-order checks; does not start Minecraft or an audio device. */
public final class WonderlandSmoke {
    static final List<String> ENTRIES = List.of("AcceptanceEnter", "CornersEnter", "DepthsEnter", "DlrowmaerdEnter",
            "Dream6Enter", "DreamworldEnter", "EndlessForestEnter", "FoolsParadiseEnter", "MazeEnter",
            "NoMansLandEnter", "TunnelsEnter", "WhatOnceWasEnter");
    static final String PREFIX = "net/mcreator/thewonderland/procedures/";
    static final Set<String> DIMENSIONS = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), wonderland = Path.of(args[1]), mod = Path.of(args[2]);
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.valueOf(args[3]));
        MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
        Mixins.addConfiguration("wonderland-entry-smoke.json");
        Mixins.addConfiguration("wonderland-player-smoke.json");
        var constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        Object transformer = constructor.newInstance();
        var transform = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
        transform.setAccessible(true);
        for (String simple : ENTRIES) {
            String internal = PREFIX + simple + "Procedure";
            byte[] original = resource(internal);
            ClassNode before = node(original);
            MethodNode execute = before.methods.stream().filter(m -> m.name.equals("execute")).findFirst().orElseThrow();
            List<InvokeDynamicInsnNode> tasks = new ArrayList<>();
            for (AbstractInsnNode instruction : execute.instructions) if (instruction instanceof InvokeDynamicInsnNode lambda) tasks.add(lambda);
            boolean deferred = simple.equals("TunnelsEnter") || simple.equals("WhatOnceWasEnter");
            check(tasks.size() == (deferred ? 2 : 1), "unexpected tasks in " + simple);
            var handle = (org.objectweb.asm.Handle) tasks.get(deferred ? 1 : 0).bsmArgs[1];
            MethodNode cleanup = before.methods.stream().filter(m -> m.name.equals(handle.getName())).findFirst().orElseThrow();
            int stopStrings = 0, commandCalls = 0;
            for (AbstractInsnNode instruction : cleanup.instructions) {
                if (instruction instanceof LdcInsnNode ldc && "stopsound @p".equals(ldc.cst)) stopStrings++;
                if (instruction instanceof FieldInsnNode field) check(field.getOpcode() != Opcodes.PUTFIELD && field.getOpcode() != Opcodes.PUTSTATIC, "cleanup writes gameplay state");
                if (instruction instanceof MethodInsnNode call && call.name.equals("m_230957_")) commandCalls++;
            }
            check(stopStrings == 1 && commandCalls == 1, "unexpected cleanup behavior " + simple);
            byte[] changed = (byte[]) transform.invoke(transformer, internal.replace('/', '.'), internal.replace('/', '.'), original);
            ClassNode after = node(changed);
            MethodNode updated = after.methods.stream().filter(m -> m.name.equals("execute")).findFirst().orElseThrow();
            check(calls(updated, "queueServerWork") == (deferred ? 1 : 0), "late task still queued / teleport task lost: " + simple);
            check(Arrays.stream(updated.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call && call.name.contains("chatoverhaul$removeLateStopAll")).count() == 1, "missing actual redirect " + simple);
            write(output, internal, changed);
        }
        String player = "net/minecraft/server/level/ServerPlayer";
        ClassNode changedPlayer = node((byte[]) transform.invoke(transformer, player.replace('/', '.'), player.replace('/', '.'), resource(player)));
        int verified = 0;
        for (MethodNode method : changedPlayer.methods) {
            if (!(method.name.equals("m_8999_") || method.name.equals("changeDimension"))) continue;
            int position = 0, hook = -1, packet = -1;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.contains("chatoverhaul$cleanupBefore")) hook = position;
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.endsWith("/ClientboundRespawnPacket")) packet = position;
                position++;
            }
            if (packet >= 0) { check(hook >= 0 && hook < packet, "cleanup not BEFORE respawn in " + method.name); verified++; }
        }
        check(verified == 2, "both ServerPlayer transfer paths must be covered");
        try (JarFile jar = new JarFile(wonderland.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("net/mcreator/thewonderland/world/dimension/[^$]+\\.class")) continue;
                ClassNode dimension;
                try (var stream = jar.getInputStream(entry)) { dimension = node(stream.readAllBytes()); }
                Set<String> keys = new HashSet<>(); boolean affected = false;
                for (MethodNode method : dimension.methods) for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.startsWith("the_wonderland:")) keys.add(s);
                    if (instruction instanceof MethodInsnNode call && ENTRIES.stream().anyMatch(s -> call.owner.equals(PREFIX + s + "Procedure"))) affected = true;
                }
                if (affected) DIMENSIONS.addAll(keys);
            }
            String depths;
            try (var stream = jar.getInputStream(jar.getJarEntry("data/the_wonderland/worldgen/biome/depths_biome.json"))) { depths = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            check(depths.contains("\"ambient_sound\": \"the_wonderland:depth\"") && !depths.contains("\"music\""), "Depths audio definition changed");
        }
        check(DIMENSIONS.size() == 14, "unexpected affected dimension registrations " + DIMENSIONS);
        System.out.println("PASS: actual Mixin transformation of 12 entry procedures; late cleanup removed, both one-tick teleports retained; both ServerPlayer hooks precede respawn packet construction.");
        WonderlandSandbox.run(output, wonderland, mod, DIMENSIONS);
    }
    static byte[] resource(String name) throws Exception { try (var stream = WonderlandSmoke.class.getClassLoader().getResourceAsStream(name + ".class")) { return Objects.requireNonNull(stream, name).readAllBytes(); } }
    static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    static long calls(MethodNode method, String name) { return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode m && m.name.equals(name)).count(); }
    static void write(Path out, String name, byte[] bytes) throws Exception { Path file = out.resolve(name + ".class"); Files.createDirectories(file.getParent()); Files.write(file, bytes); }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
