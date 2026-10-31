import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarFile;

/** Offline bytecode/control-flow regression checks. This is NOT a Forge/Mixin runtime launch. */
public final class VerifyFalseCalmEvents {
    private static final String PREFIX = "net/mcreator/interpritation/procedures/";
    private static final String CIRCUIT = PREFIX + "CircuitStareOnInitialEntitySpawnProcedure";
    private static final String VOID = PREFIX + "VoiddOnInitialEntitySpawnProcedure";
    private static final String MOON = "thebrokenscript:falsecalm2";
    private static final String DESC = "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try (JarFile mod = new JarFile(args[0]); JarFile rus = new JarFile(args[1])) {
            Set<String> producers = new TreeSet<>();
            for (var entries = rus.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                byte[] bytes;
                try (var stream = rus.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                String text = new String(bytes, StandardCharsets.ISO_8859_1);
                if (entry.getName().endsWith(".class") && text.contains(MOON)) producers.add(entry.getName());
                if (entry.getName().startsWith("data/") && entry.getName().endsWith(".json")) {
                    check(!text.contains("falsecalm2"), "unexpected data-driven trigger: " + entry.getName());
                }
            }
            check(producers.equals(Set.of(CIRCUIT + ".class", VOID + ".class")), "automatic producer set changed: " + producers);
            System.out.println("PASS: jar-wide scan identifies exactly two direct falsecalm2 producers; no data JSON trigger.");

            MethodNode circuit = execute(read(rus, CIRCUIT));
            MethodNode voidEvent = execute(read(rus, VOID));
            ClassNode circuitMixin = read(mod, "com/jonathan/chatoverhaul/mixin/rus/FalseCalmCircuitEventMixin");
            ClassNode voidMixin = read(mod, "com/jonathan/chatoverhaul/mixin/rus/FalseCalmVoidEventMixin");
            MethodNode cancel = handler(circuitMixin, "Inject");
            MethodNode rejectVoid = handler(voidMixin, "Inject");
            AnnotationNode inject = annotation(cancel, "Inject");
            AnnotationNode voidInject = annotation(rejectVoid, "Inject");
            check(((List<?>) value(inject, "method")).equals(List.of("execute" + DESC)), "ambiguous Circuit selector");
            check(((List<?>) value(voidInject, "method")).equals(List.of("execute" + DESC)), "ambiguous Void selector");
            check(value(inject, "cancellable").equals(true), "Circuit injection is not cancellable");
            for (String key : List.of("require", "expect", "allow")) {
                check(value(inject, key).equals(1), "Circuit match count not pinned: " + key);
                check(value(voidInject, key).equals(1), "Void match count not pinned: " + key);
            }
            AnnotationNode at = (AnnotationNode) ((List<?>) value(inject, "at")).get(0);
            check(value(at, "value").equals("JUMP") && value(at, "opcode").equals(Opcodes.IFGE), "wrong Circuit branch anchor");
            check(value(at, "ordinal").equals(0) && ((String[]) value(at, "shift"))[1].equals("AFTER"), "wrong branch edge");
            AnnotationNode slice = (AnnotationNode) ((List<?>) value(inject, "slice")).get(0);
            AnnotationNode from = (AnnotationNode) value(slice, "from");
            check(((List<?>) value(from, "args")).equals(List.of("doubleValue=0.7")), "wrong selection threshold slice");
            AnnotationNode voidAt = (AnnotationNode) ((List<?>) value(voidInject, "at")).get(0);
            check(value(voidAt, "value").equals("HEAD") && value(voidInject, "cancellable").equals(true), "Void must cancel at entry");
            check(count(circuit, MOON) == 2 && count(circuit, 0.7) == 1, "unexpected Circuit original structure");
            String callbackDesc = DESC.substring(0, DESC.indexOf(')'))
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
            check(cancel.desc.equals(callbackDesc), "wrong Circuit callback descriptor");
            check(rejectVoid.desc.equals(callbackDesc), "wrong Void callback descriptor");
            check(calls(cancel, "net/minecraft/world/entity/Entity", "m_146870_") == 1, "missing reobfuscated rejected-entity discard");
            check(calls(cancel, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel") == 1, "missing event cancellation");
            check(calls(rejectVoid, "net/minecraft/world/entity/Entity", "m_146870_") == 1, "missing Void cleanup");
            check(calls(rejectVoid, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel") == 1, "missing Void cancellation");
            for (ClassNode mixin : List.of(circuitMixin, voidMixin)) {
                for (MethodNode method : mixin.methods) {
                    for (AbstractInsnNode node : method.instructions) {
                        if (node instanceof MethodInsnNode call) {
                            check(!call.owner.startsWith("net/minecraft/client/"), "dedicated-server unsafe client call");
                            check(!call.owner.contains("SoundManager") && !call.owner.endsWith("/Level"), "unexpected sound-system hook");
                        }
                    }
                }
            }
            String config;
            try (var stream = mod.getInputStream(mod.getJarEntry("mixins.chatoverhaul.rus.json"))) {
                config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String common = config.substring(config.indexOf("\"mixins\""), config.indexOf("\"client\""));
            check(common.contains("FalseCalmCircuitEventMixin") && common.contains("FalseCalmVoidEventMixin"), "hooks not registered on both sides");
            System.out.println("PASS: reobfuscated descriptors, Circuit selection edge / Void HEAD, cleanup, cancellation and common-side registration.");

            // Traverse the ORIGINAL CFG, applying only the declared injection semantics.
            // Eligibility and Level-vs-LevelAccessor predicates explore both directions.
            // Random decisions use boundary samples.
            double[] outer = {0.0, 0.009999999, 0.01, 0.05, 0.5, Math.nextDown(1.0)};
            double[] choices = {0.0, 0.5, Math.nextDown(0.7), 0.7, 0.9, Math.nextDown(1.0)};
            for (double a : outer) for (double b : choices) {
                Set<AbstractInsnNode> before = walk(circuit, new double[]{a, b}, false, false);
                Set<AbstractInsnNode> after = walk(circuit, new double[]{a, b}, true, false);
                check(!hasConstant(after, MOON), "Circuit falsecalm2 event remains reachable");
                if (a < 0.01 && b < 0.7) {
                    check(hasConstant(before, MOON), "original Circuit trigger not exercised");
                    check(!hasWrite(after, "iscircuitspawned") && !hasCall(after, "queueServerWork"), "cancelled Circuit event still starts lifecycle");
                } else {
                    check(before.equals(after), "unrelated Circuit branch changed");
                }
                if (a < 0.01 && b >= 0.7) check(hasConstant(after, "thebrokenscript:integritywatching"), "alternative track disabled");
            }
            for (double roll : new double[]{0.0, 0.01, Math.nextDown(0.05), 0.05, 0.5, Math.nextDown(1.0)}) {
                Set<AbstractInsnNode> before = walk(voidEvent, new double[]{roll}, false, false);
                Set<AbstractInsnNode> after = walk(voidEvent, new double[]{roll}, false, true);
                check(!hasConstant(after, MOON) && !hasConstant(after, "stopsound @a"), "Void event can still interrupt music");
                check(!hasWrite(after, "isvoidspawned") && !hasCall(after, "queueServerWork"), "disabled Void event still starts lifecycle");
                check(after.isEmpty(), "Void original initializer must not execute at all");
                if (roll < 0.05) check(hasConstant(before, "stopsound @a") && hasConstant(before, MOON), "original Void trigger not exercised");
            }
            System.out.println("PASS: 36 Circuit + 6 Void boundary/control-flow cases; falsecalm2 and its stop-all command unreachable; integritywatching branch unchanged.");
            System.out.println("PASS: " + assertions + " assertions. Runtime Mixin application and in-game audio still require Forge testing.");
        }
    }

    private static Set<AbstractInsnNode> walk(MethodNode method, double[] rolls, boolean cancel, boolean disable) {
        if (disable) return Set.of(); // HEAD handler always cancels before any original instruction.
        List<AbstractInsnNode> nodes = Arrays.asList(method.instructions.toArray());
        AbstractInsnNode selectionEntry = null;
        for (AbstractInsnNode n : nodes) {
            if (n instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFGE
                    && previous(n).getOpcode() == Opcodes.DCMPG
                    && previous(previous(n)) instanceof LdcInsnNode ldc && Double.valueOf(0.7).equals(ldc.cst)) {
                selectionEntry = n.getNext();
            }
        }
        if (cancel) check(selectionEntry != null, "missing selection edge");
        Map<AbstractInsnNode, Double> randoms = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode n : nodes) if (n instanceof MethodInsnNode m && m.owner.equals("java/lang/Math") && m.name.equals("random")) randoms.put(n, rolls[index++]);
        Set<AbstractInsnNode> reached = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<AbstractInsnNode> pending = new ArrayDeque<>();
        pending.add(nodes.get(0));
        while (!pending.isEmpty()) {
            AbstractInsnNode node = pending.removeFirst();
            if (cancel && node == selectionEntry) continue;
            if (!reached.add(node)) continue;
            if (node instanceof JumpInsnNode jump) {
                if (jump.getOpcode() == Opcodes.GOTO) { pending.add(jump.label); continue; }
                AbstractInsnNode cmp = previous(node), threshold = previous(cmp), random = previous(threshold);
                if (jump.getOpcode() == Opcodes.IFGE && cmp.getOpcode() == Opcodes.DCMPG
                        && threshold instanceof LdcInsnNode ldc && ldc.cst instanceof Double chance && randoms.containsKey(random)) {
                    pending.add(randoms.get(random) >= chance ? jump.label : node.getNext());
                } else {
                    pending.add(jump.label);
                    pending.add(node.getNext());
                }
            } else if (node.getOpcode() != Opcodes.RETURN && node.getOpcode() != Opcodes.ATHROW && node.getNext() != null) pending.add(node.getNext());
        }
        return reached;
    }

    private static AbstractInsnNode previous(AbstractInsnNode n) {
        if (n == null) return null;
        n = n.getPrevious();
        while (n != null && n.getOpcode() < 0) n = n.getPrevious();
        return n;
    }
    private static boolean hasConstant(Set<AbstractInsnNode> nodes, String text) { return nodes.stream().anyMatch(n -> n instanceof LdcInsnNode ldc && text.equals(ldc.cst)); }
    private static boolean hasWrite(Set<AbstractInsnNode> nodes, String name) { return nodes.stream().anyMatch(n -> n instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals(name)); }
    private static boolean hasCall(Set<AbstractInsnNode> nodes, String name) { return nodes.stream().anyMatch(n -> n instanceof MethodInsnNode m && m.name.equals(name)); }
    private static long count(MethodNode m, Object constant) { return Arrays.stream(m.instructions.toArray()).filter(n -> n instanceof LdcInsnNode ldc && constant.equals(ldc.cst)).count(); }
    private static long calls(MethodNode m, String owner, String name) { return Arrays.stream(m.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)).count(); }
    private static MethodNode execute(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("execute") && m.desc.equals(DESC)).findFirst().orElseThrow(); }
    private static MethodNode handler(ClassNode node, String kind) { return node.methods.stream().filter(m -> annotation(m, kind) != null).findFirst().orElseThrow(); }
    private static AnnotationNode annotation(MethodNode method, String name) {
        for (List<AnnotationNode> list : Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations)) {
            if (list != null) for (AnnotationNode a : list) if (a.desc.endsWith("/" + name + ";")) return a;
        }
        return null;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) { new ClassReader(stream).accept(node, 0); }
        return node;
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
