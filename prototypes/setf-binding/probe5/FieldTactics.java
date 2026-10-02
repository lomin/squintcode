// PROTOTYPE (probe 5) — the two tactics Clojure cannot itself express.
//
//   direct    : a real field store. The floor, and the thing a JVM backend could
//               only reach by generating code.
//   mhExact   : a cached MethodHandle invoked with invokeExact. This is what a
//               type-hint-specialized backend would actually emit for a known
//               type -- no varargs array, no boxing dance at the call site.
//
// Clojure is unable to call invokeExact directly (it is signature-polymorphic),
// which is why the earlier Clojure-side MethodHandle number was an unfair proxy.

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class FieldTactics {

    static final MethodHandle SET_X;

    static {
        try {
            SET_X = MethodHandles.lookup()
                    .unreflectSetter(java.awt.Point.class.getField("x"))
                    .asType(MethodType.methodType(void.class, Object.class, int.class));
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /** The floor: a plain field store. */
    public static void direct(java.awt.Point p, int v) {
        p.x = v;
    }

    /** A cached MethodHandle, called exactly. */
    public static void mhExact(java.awt.Point p, int v) {
        try {
            SET_X.invokeExact((Object) p, v);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
