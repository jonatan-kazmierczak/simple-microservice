package demo;

import java.lang.reflect.InvocationTargetException;

import static demo.Utils.log;

public class Config {
    public final int port;
    public final int programId;
    public final String programIdStr;
    public final String serialNumber;
    public final int pauseSeconds;


    public Config(String[] startArgs) {
        this.port = getValue(startArgs, 0, 3000);
        this.programId = getValue(startArgs, 1, 3);
        this.programIdStr = String.valueOf(programId);
        this.pauseSeconds = getValue(startArgs, 2, 0);
        this.serialNumber = getValue(startArgs, 3, "00000000");

        if (programId < 1 || programId > 6) {
            log(this);
            throw new IllegalArgumentException("programId must be in range 4..6");
        }
    }

    private static <T> T getValue(String[] startArgs, int paramIdx, T defaultVal) {
        T value;

        try {
            value = (T) defaultVal.getClass().getConstructor( String.class ).newInstance( startArgs[ paramIdx ] );
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException
                | RuntimeException e) {
            // when startArgs[paramIdx] doesn't exist or contains incorrect value
            value = defaultVal;
        }
        return value;
    }

    @Override
    public String toString() {
        return "Config {" +
                "port=" + port +
                ", programId=" + programId +
                ", pauseSeconds=" + pauseSeconds +
                ", serialNumber='" + serialNumber + '\'' +
                '}';
    }
}
