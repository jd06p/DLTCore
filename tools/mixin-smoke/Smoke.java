import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.*;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/** Applies the actual Mixin 0.8.5 transformer offline; not a Forge or audio-device launch. */
public final class Smoke {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.valueOf(args.length > 2 ? args[2] : "SERVER"));
        Mixins.addConfiguration("falsecalm-smoke.json");
        var constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        Object transformer = constructor.newInstance();
        var transform = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
        transform.setAccessible(true);
        for (String simple : List.of("CircuitStareOnInitialEntitySpawnProcedure", "VoiddOnInitialEntitySpawnProcedure")) {
            String name = "net.mcreator.interpritation.procedures." + simple;
            byte[] original;
            try (var stream = Smoke.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
                original = Objects.requireNonNull(stream).readAllBytes();
            }
            byte[] changed = (byte[]) transform.invoke(transformer, name, name, original);
            ClassNode node = new ClassNode();
            new ClassReader(changed).accept(node, 0);
            MethodNode execute = node.methods.stream().filter(m -> m.name.equals("execute")).findFirst().orElseThrow();
            int calls = 0;
            for (AbstractInsnNode instruction : execute.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name) && call.name.contains("chatoverhaul$")) calls++;
            }
            if (calls == 0) throw new AssertionError("No actual Mixin hook applied to " + simple);
            Path out = Path.of(args[0], name.replace('.', '/') + ".class");
            Files.createDirectories(out.getParent());
            Files.write(out, changed);
            System.out.println("PASS: actual Mixin transformer applied " + calls + " hook calls to " + simple);
        }
        if (args.length > 1) Sandbox.run(Path.of(args[0]), Path.of(args[1]),
                Path.of(org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }

    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        public String getName() { return "DLTCore offline Mixin smoke test"; }
        public boolean isValid() { return true; }
        public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        public IClassProvider getClassProvider() { return this; }
        public IClassBytecodeProvider getBytecodeProvider() { return this; }
        public ITransformerProvider getTransformerProvider() { return null; }
        public IClassTracker getClassTracker() { return null; }
        public IMixinAuditTrail getAuditTrail() { return null; }
        public Collection<String> getPlatformAgents() { return List.of(); }
        public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("falsecalm-smoke"); }
        public InputStream getResourceAsStream(String name) { return Smoke.class.getClassLoader().getResourceAsStream(name); }
        public URL[] getClassPath() { return new URL[0]; }
        public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException { return Class.forName(name, initialize, Smoke.class.getClassLoader()); }
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
        public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, false); }
        public ClassNode getClassNode(String name, boolean transformers) throws ClassNotFoundException, IOException {
            String internal = name.replace('.', '/');
            try (InputStream stream = getResourceAsStream(internal + ".class")) {
                ClassNode node = new ClassNode();
                if (stream != null) {
                    new ClassReader(stream).accept(node, 0);
                    return node;
                }
                // These procedures are plain Object subclasses; Minecraft types are only metadata
                // for this isolated transformation test. They are NOT executed/simulated here.
                if (internal.startsWith("net/minecraft/") || internal.startsWith("net/minecraftforge/")) {
                    node.version = Opcodes.V17;
                    node.access = Opcodes.ACC_PUBLIC;
                    node.name = internal;
                    node.superName = "java/lang/Object";
                    return node;
                }
                throw new ClassNotFoundException(name);
            }
        }
    }

    public static final class Properties implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey {}
        private final Map<IPropertyKey, Object> values = new HashMap<>();
        public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
        public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        public <T> T getProperty(IPropertyKey key, T fallback) { T value = getProperty(key); return value == null ? fallback : value; }
        public String getPropertyString(IPropertyKey key, String fallback) { Object value = values.get(key); return value == null ? fallback : value.toString(); }
    }
}
