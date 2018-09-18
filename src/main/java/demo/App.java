package demo;

import java.util.Arrays;

import static demo.Utils.log;

public class App {
    public static void main(String[] args) throws Exception {
        Config config = new Config(args);
        log("Started with parameters: %s", Arrays.toString(args));
        log(config);
        new Simulator( new DataLoader(config).apiResponses, config );
    }
}
