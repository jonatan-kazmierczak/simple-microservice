package demo;

public interface Utils {
    static void log(String string, Object... params) {
        System.out.printf(string, params);
        System.out.println();
    }

    static void log(Object obj) {
        System.out.println(obj);
    }
}
