import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.jar.JarFile;

/** Offline selector/signature regression check; does not claim to launch Forge or test multiplayer. */
public final class VerifyRusDelivery {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (JarFile mod = new JarFile(args[0]); JarFile rus = new JarFile(args[1])) {
            for (String name : List.of("CircuitTickDeliveryMixin", "CircuitSpawnDeliveryMixin",
                    "CircuitTextOverlayMixin", "CircuitFlashOverlayMixin", "MoonGlitchJoinMixin", "MoonGlitchEventMixin")) {
                ClassNode mixin = read(mod, "com/jonathan/chatoverhaul/mixin/rus/" + name);
                AnnotationNode annotation = find(mixin.invisibleAnnotations, "Mixin");
                @SuppressWarnings("unchecked") List<String> targets = (List<String>) value(annotation, "targets");
                for (String target : targets) {
                    ClassNode original = read(rus, target.replace('.', '/'));
                    for (MethodNode handler : mixin.methods) {
                        for (String kind : List.of("Redirect", "Inject")) {
                            AnnotationNode injection = find(handler.visibleAnnotations, kind);
                            if (injection == null) injection = find(handler.invisibleAnnotations, kind);
                            if (injection == null) continue;
                            @SuppressWarnings("unchecked") List<String> selectors = (List<String>) value(injection, "method");
                            for (String selector : selectors) {
                                List<MethodNode> selected = original.methods.stream()
                                        .filter(m -> selector.equals(m.name) || selector.equals(m.name + m.desc)).toList();
                                require(selected.size() == 1, name + ": ambiguous/missing selector " + selector);
                                MethodNode method = selected.get(0);
                                if (kind.equals("Inject")) {
                                    List<Type> expected = new ArrayList<>(List.of(Type.getArgumentTypes(method.desc)));
                                    expected.add(Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/"
                                            + (Type.getReturnType(method.desc).equals(Type.VOID_TYPE)
                                            ? "CallbackInfo" : "CallbackInfoReturnable")));
                                    require(List.of(Type.getArgumentTypes(handler.desc)).equals(expected),
                                            name + ": bad injection descriptor " + handler.name);
                                    checks++;
                                    continue;
                                }
                                AnnotationNode at = (AnnotationNode) value(injection, "at");
                                String member = (String) value(at, "target");
                                String owner = member.substring(1, member.indexOf(';'));
                                String rest = member.substring(member.indexOf(';') + 1);
                                int hits = 0;
                                for (AbstractInsnNode insn : method.instructions) {
                                    List<Type> expected = new ArrayList<>();
                                    Type returnType;
                                    if (insn instanceof MethodInsnNode call && call.owner.equals(owner)
                                            && rest.equals(call.name + call.desc)) {
                                        if (call.getOpcode() != Opcodes.INVOKESTATIC) expected.add(Type.getObjectType(owner));
                                        expected.addAll(List.of(Type.getArgumentTypes(call.desc)));
                                        returnType = Type.getReturnType(call.desc);
                                    } else if (insn instanceof FieldInsnNode field && field.owner.equals(owner)
                                            && rest.equals(field.name + ":" + field.desc)
                                            && Objects.equals(value(at, "opcode"), field.getOpcode())) {
                                        expected.add(Type.getObjectType(owner));
                                        returnType = Type.getType(field.desc);
                                        if (field.getOpcode() == Opcodes.PUTFIELD) {
                                            expected.add(returnType);
                                            returnType = Type.VOID_TYPE;
                                        }
                                    } else continue;
                                    List<Type> actual = List.of(Type.getArgumentTypes(handler.desc));
                                    List<Type> captured = new ArrayList<>(expected);
                                    captured.addAll(List.of(Type.getArgumentTypes(method.desc)));
                                    require(actual.equals(expected) || actual.equals(captured),
                                            name + ": bad redirect arguments " + handler.name + " " + actual + " expected " + captured);
                                    require(Type.getReturnType(handler.desc).equals(returnType), name + ": bad redirect return");
                                    hits++;
                                }
                                require(hits > 0, name + ": missing target " + member);
                                checks += hits;
                                System.out.println(name + "/" + handler.name + ": " + hits + " exact bytecode matches");
                            }
                        }
                    }
                }
            }
            ClassNode delivery = read(mod, "com/jonathan/chatoverhaul/event/CircuitChaseDelivery");
            for (MethodNode method : delivery.methods) {
                require(!method.desc.contains("net/minecraft/client/"), "client type in common helper descriptor");
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call) require(!call.owner.startsWith("net/minecraft/client/"), "client call in common helper");
                    if (insn instanceof FieldInsnNode field) require(!field.owner.startsWith("net/minecraft/client/"), "client field in common helper");
                }
            }
            require(mod.getJarEntry("mixins.chatoverhaul.rus.json") != null, "missing mixin config");
            System.out.println("PASS: " + checks + " injection sites/signatures; common delivery has no Minecraft client references.");
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class");
        require(entry != null, "missing class " + name);
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(entry)) { new ClassReader(stream).accept(node, 0); }
        return node;
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String name) {
        if (annotations == null) return null;
        return annotations.stream().filter(a -> a.desc.endsWith("/" + name + ";")).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
