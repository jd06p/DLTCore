import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.nio.file.*;
import java.util.*;

/** Applies the real client mixin to the real Forge MusicManager, then executes it with API stubs. */
public final class MusicPolicySmoke {
    public static void main(String[] args) throws Exception {
        try (var jar = new java.util.jar.JarFile(args[1]);
             var stream = jar.getInputStream(jar.getJarEntry("mixins.chatoverhaul.json"))) {
            var config = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String mixin = "MusicManagerWonderlandPolicyMixin";
            boolean found = false;
            for (var entry : config.getAsJsonArray("client")) if (entry.getAsString().equals(mixin)) found = true;
            WonderlandSmoke.check(found, "policy missing from production client section");
            for (var entry : config.getAsJsonArray("mixins")) WonderlandSmoke.check(!entry.getAsString().equals(mixin), "policy accidentally registered server-side");
        }
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.valueOf(args[3]));
        MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
        Mixins.addConfiguration("music-policy-smoke.json");
        var constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        Object transformer = constructor.newInstance();
        var transform = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
        transform.setAccessible(true);
        String name = "net/minecraft/client/sounds/MusicManager";
        byte[] original = WonderlandSmoke.resource(name);
        byte[] changed = (byte[]) transform.invoke(transformer, name.replace('/', '.'), name.replace('/', '.'), original);
        if (args[3].equals("SERVER")) {
            WonderlandSmoke.check(Arrays.equals(original, changed), "client music mixin applied on server side");
            System.out.println("PASS: dedicated-server Mixin environment skips the client-only music policy.");
            return;
        }
        ClassNode node = WonderlandSmoke.node(changed);
        int hooks = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && (call.name.contains("chatoverhaul$rejectVanillaSelection")
                    || call.name.contains("chatoverhaul$rejectDirectVanillaStart"))) hooks++;
        }
        WonderlandSmoke.check(hooks == 2, "both pre-selection and direct pre-start hooks must apply");
        Path output = Path.of(args[0]);
        WonderlandSmoke.write(output, name, changed);
        System.out.println("PASS: actual Mixin transformer applied both music guards with production SRG refmap/local capture.");
        MusicPolicySandbox.run(output, Path.of(args[1]), Path.of(args[2]));
    }
}
