package net.lax1dude.eaglercraft.v1_8;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.function.Consumer;

import net.lax1dude.eaglercraft.v1_8.internal.EnumPlatformType;

/**
 * JVM-side bench stub for EagRuntime. The real EagRuntime.java is one of the platform files that
 * cannot compile under ECJ without the TeaVM stack; benches only need the buffer-allocation and
 * logger/locale helpers below. The allocated buffers delegate to java.nio buffers (by reflective
 * name matching, since only names and primitive shapes align across the two APIs) so that code
 * such as TextureUtil's texture-upload path behaves as it does in the real client; calls that
 * have no java.nio counterpart fail loudly instead of silently corrupting state.
 */
public class EagRuntime {

    private static EnumPlatformType platformType = EnumPlatformType.DESKTOP;

    public static EnumPlatformType getPlatformType() {
        return platformType;
    }

    public static void setPlatformType(EnumPlatformType type) {
        platformType = type;
    }

    private static final class NioDelegate implements InvocationHandler {
        private final Object nio;

        NioDelegate(Object nio) {
            this.nio = nio;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            Class<?> returnType = method.getReturnType();
            // eagler-specific typed accessors -> java.nio typed getters/setters
            if ("getElement".equals(name) && args != null && args.length == 1) {
                int idx = ((Number) args[0]).intValue();
                if (nio instanceof java.nio.IntBuffer) {
                    return Integer.valueOf(((java.nio.IntBuffer) nio).get(idx));
                }
                if (nio instanceof java.nio.ShortBuffer) {
                    return Short.valueOf(((java.nio.ShortBuffer) nio).get(idx));
                }
                if (nio instanceof java.nio.ByteBuffer) {
                    return Byte.valueOf(((java.nio.ByteBuffer) nio).get(idx));
                }
                if (nio instanceof java.nio.FloatBuffer) {
                    return Float.valueOf(((java.nio.FloatBuffer) nio).get(idx));
                }
            }
            if ("putElement".equals(name) && args != null && args.length == 2) {
                int idx = ((Number) args[0]).intValue();
                Object val = args[1];
                if (nio instanceof java.nio.IntBuffer) {
                    ((java.nio.IntBuffer) nio).put(idx, ((Number) val).intValue());
                } else if (nio instanceof java.nio.ShortBuffer) {
                    ((java.nio.ShortBuffer) nio).put(idx, ((Number) val).shortValue());
                } else if (nio instanceof java.nio.ByteBuffer) {
                    ((java.nio.ByteBuffer) nio).put(idx, ((Number) val).byteValue());
                } else if (nio instanceof java.nio.FloatBuffer) {
                    ((java.nio.FloatBuffer) nio).put(idx, ((Number) val).floatValue());
                }
                return proxy;
            }
            Method[] candidates = nio.getClass().getMethods();
            for (int i = 0; i < candidates.length; ++i) {
                Method m = candidates[i];
                if (!m.getName().equals(name)) {
                    continue;
                }
                Class<?>[] params = m.getParameterTypes();
                if (params.length != (args == null ? 0 : args.length)) {
                    continue;
                }
                boolean ok = true;
                for (int j = 0; j < params.length; ++j) {
                    Object a = args[j];
                    if (a != null && !params[j].isAssignableFrom(a.getClass())) {
                        if (params[j].isPrimitive() && a instanceof Number) {
                            continue; // primitive narrowing is handled by reflection
                        }
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                Object result = m.invoke(nio, args);
                if (result instanceof java.nio.Buffer && returnType.isInterface()
                        && returnType.getName().startsWith("net.lax1dude.eaglercraft")) {
                    return wrap(returnType, result);
                }
                return result;
            }
            throw new UnsupportedOperationException("bench stub buffer has no java.nio equivalent for " + name);
        }
    }

    private static Object wrap(Class<?> eaglerItf, Object nioBuffer) {
        return Proxy.newProxyInstance(EagRuntime.class.getClassLoader(), new Class<?>[] { eaglerItf },
                new NioDelegate(nioBuffer));
    }

    public static net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer allocateByteBuffer(int length) {
        return (net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer) wrap(
                net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer.class, ByteBuffer.allocateDirect(length));
    }

    public static net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer allocateIntBuffer(int length) {
        return (net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer) wrap(
                net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer.class, ByteBuffer.allocateDirect(length << 2).asIntBuffer());
    }

    public static net.lax1dude.eaglercraft.v1_8.internal.buffer.FloatBuffer allocateFloatBuffer(int length) {
        return (net.lax1dude.eaglercraft.v1_8.internal.buffer.FloatBuffer) wrap(
                net.lax1dude.eaglercraft.v1_8.internal.buffer.FloatBuffer.class, ByteBuffer.allocateDirect(length << 2).asFloatBuffer());
    }

    public static void freeByteBuffer(net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer buffer) {
        // direct JVM buffers need no explicit free
    }

    public static String currentThreadName() {
        return Thread.currentThread().getName();
    }

    public static Calendar getLocaleCalendar() {
        return Calendar.getInstance();
    }

    public static <T extends DateFormat> T fixDateFormat(T input) {
        input.setCalendar(getLocaleCalendar());
        return input;
    }

    public static void getStackTrace(Throwable t, Consumer<String> ret) {
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length; ++i) {
            ret.accept(st[i].toString());
        }
    }

    public static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        getStackTrace(t, (s) -> {
            sb.append("    at ").append(s).append('\n');
        });
        return sb.toString();
    }
}
