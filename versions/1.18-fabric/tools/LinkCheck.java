import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Resolves every class/method/field reference in the compiled platform classes against the
 * Minecraft 1.19.2 (named) jar plus the build classpath. Reports anything that would throw
 * NoClassDefFoundError / NoSuchMethodError / NoSuchFieldError at runtime.
 */
public final class LinkCheck {

    static final Map<String, ClassNode> CLASSES = new HashMap<>();
    static final List<Path> JARS = new ArrayList<>();
    static Path classDir;

    public static void main(String[] args) throws Exception {
        classDir = Paths.get(args[0]);
        for (int i = 1; i < args.length; i++) {
            for (String part : args[i].split(File.pathSeparator)) {
                if (part.isEmpty()) continue;
                Path p = Paths.get(part);
                if (Files.isRegularFile(p) && p.toString().endsWith(".jar")) JARS.add(p);
            }
        }
        List<Path> own = new ArrayList<>();
        Files.walk(classDir).filter(p -> p.toString().endsWith(".class")).forEach(own::add);
        int refs = 0, bad = 0;
        Map<String, Integer> byKind = new TreeMap<>();
        for (Path p : own) {
            ClassNode cn = read(Files.readAllBytes(p));
            CLASSES.put(cn.name, cn);
        }
        for (Path p : own) {
            ClassNode cn = CLASSES.get(read(Files.readAllBytes(p)).name);
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) insn;
                        refs++;
                        if (!resolvesMethod(m.owner, m.name, m.desc)) {
                            bad++; byKind.merge("method", 1, Integer::sum);
                            System.out.println("MISSING METHOD " + m.owner + "." + m.name + m.desc
                                    + "  <- " + cn.name + "." + mn.name + mn.desc);
                        }
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) insn;
                        refs++;
                        if (!resolvesField(f.owner, f.name, f.desc)) {
                            bad++; byKind.merge("field", 1, Integer::sum);
                            System.out.println("MISSING FIELD  " + f.owner + "." + f.name + " " + f.desc
                                    + "  <- " + cn.name + "." + mn.name);
                        }
                    } else if (insn instanceof TypeInsnNode) {
                        TypeInsnNode t = (TypeInsnNode) insn;
                        refs++;
                        if (!exists(t.desc)) {
                            bad++; byKind.merge("class", 1, Integer::sum);
                            System.out.println("MISSING CLASS  " + t.desc + "  <- " + cn.name + "." + mn.name);
                        }
                    }
                }
            }
            for (FieldNode fn : cn.fields) {
                refs++;
                if (!exists(Type.getType(fn.desc).getInternalName()) && fn.desc.startsWith("L")) {
                    bad++; byKind.merge("fieldtype", 1, Integer::sum);
                    System.out.println("MISSING TYPE   " + fn.desc + "  <- " + cn.name + "." + fn.name);
                }
            }
            for (MethodNode mn : cn.methods) {
                for (Type t : Type.getArgumentTypes(mn.desc)) {
                    if (t.getSort() == Type.OBJECT) {
                        refs++;
                        if (!exists(t.getInternalName())) {
                            bad++; byKind.merge("argtype", 1, Integer::sum);
                            System.out.println("MISSING TYPE   " + t.getInternalName() + "  <- " + cn.name + "." + mn.name);
                        }
                    }
                }
            }
        }
        System.out.println("----");
        System.out.println("classes=" + own.size() + " refs=" + refs + " unresolved=" + bad + " " + byKind);
    }

    static ClassNode read(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, 0);
        return cn;
    }

    static ClassNode find(String name) {
        ClassNode cn = CLASSES.get(name);
        if (cn != null) return cn;
        if (name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/")) return null;
        for (Path jar : JARS) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                JarEntry e = jf.getJarEntry(name + ".class");
                if (e != null) {
                    try (InputStream in = jf.getInputStream(e)) {
                        ClassNode n = read(in.readAllBytes());
                        CLASSES.put(name, n);
                        return n;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        CLASSES.put(name, null);
        return null;
    }

    static boolean exists(String internalName) {
        if (internalName == null) return true;
        if (internalName.startsWith("[")) return true;
        // resource-only names (textures) never appear as types
        ClassNode cn = find(internalName);
        if (cn != null) return true;
        try {
            Class.forName(internalName.replace('/', '.'), false, LinkCheck.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean resolvesMethod(String owner, String name, String desc) {
        if (owner.startsWith("[")) return true; // array type: clone()/length
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!seen.add(cur)) continue;
            ClassNode cn = find(cur);
            if (cn == null) {
                // Not in the scanned jars: if it is a JDK class we cannot introspect, assume fine;
                // if it does not exist at all, the class-level check reports it.
                return exists(cur);
            }
            for (MethodNode m : cn.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) return true;
            }
            if (cn.superName != null) queue.add(cn.superName);
            if (cn.interfaces != null) queue.addAll(cn.interfaces);
        }
        return false;
    }

    static boolean resolvesField(String owner, String name, String desc) {
        if (owner.startsWith("[")) return true;
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!seen.add(cur)) continue;
            ClassNode cn = find(cur);
            if (cn == null) {
                return exists(cur);
            }
            for (FieldNode f : cn.fields) {
                if (f.name.equals(name) && f.desc.equals(desc)) return true;
            }
            if (cn.superName != null) queue.add(cn.superName);
            if (cn.interfaces != null) queue.addAll(cn.interfaces);
        }
        return false;
    }
}
