package demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static demo.Utils.log;


/**
 * Api endpoints handlers simulating some device.
 */
public class Simulator {
    public enum RequestType { GENERIC, PROGRAM, STATUS, MAINTENANCE_INFO, BATCHES, DETAILED_LOG }

    private static final int BACKLOG_LENGTH = 128;

    private static final int STATUS_OK = 200;
    private static final int STATUS_NOT_FOUND = 404;

    // Matches text: batchNumber":Y...
    private static final String BATCH_NUMBER_PATTERN = "(?<=\\bbatchNumber\\\"\\:)\\d+";
    private static final String BATCH_ID_TOKEN = "$BATCH_ID";
    private static final String BATCH_ID_TOKEN_2 = "BATCH_ID";
    private static final String SERIAL_NO_TOKEN = "00000000";
    private static final Pattern MIN_BATCH_PATTERN = Pattern.compile("(?<=\\bbatchNumber_min=)\\d+");

    /** lengths of recordings for supported programs. */
    private static final int[] batchLengths   = { 8, 8, 8, 19, 6, 2295, 667 };

    /** Number of variants of detailed cycle logs per program. */
    private static final int[] batchLogsCount = { 1, 1, 1,  4, 1,    4,   4 };


    private final Config config;
    private final Map<URI, List<ApiResponse>> apiResponses;
    private final long startTime;

    private volatile int requestedBatchLogCount;


    public Simulator(Map<URI, List<ApiResponse>> apiResponses, Config config) throws IOException {
        this.config = config;
        this.apiResponses = apiResponses;
        //Arrays.fill( batchLengths, 1 );

        log( config );
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress( config.port ),
                BACKLOG_LENGTH);

        httpServer.createContext("/", e -> handleRequest(e, RequestType.GENERIC));

        // Note: triples of below recordings must have the same length at the end
        httpServer.createContext("/api/console/sensors", e -> handleRequest(e, RequestType.PROGRAM));
        httpServer.createContext("/api/programs/current", e -> handleRequest(e, RequestType.PROGRAM));
        httpServer.createContext("/api/status", e -> handleRequest(e, RequestType.STATUS));

        httpServer.createContext("/api/maintenance/info", e -> handleRequest(e, RequestType.MAINTENANCE_INFO));
        httpServer.createContext("/api/batches", e -> handleRequest(e, RequestType.BATCHES));
        httpServer.createContext("/api/batches/detailedlog/", e -> handleRequest(e, RequestType.DETAILED_LOG));

        httpServer.setExecutor( Executors.newWorkStealingPool() );
        startTime = System.currentTimeMillis();
        httpServer.start();
        log("HttpServer STARTED on port %d", config.port);
        log("");
    }

    private void handleRequest(HttpExchange exchange, RequestType requestType) {
        long processingStartTimeNanos = System.nanoTime();
        URI requestURI = exchange.getRequestURI();
        log("Request original:     '%s'", requestURI);
        URI pathURI = createPathURI(requestURI, requestType);
        log("Request transformed:  '%s'", pathURI);
        List<ApiResponse> responses = apiResponses.get(pathURI);

        try ( PrintStream bodyPS = new PrintStream(
                exchange.getResponseBody(), false, StandardCharsets.UTF_8.toString() ) ) {
            if (responses != null) {
                int elapsedTimeAsIdx = getElapsedTimeAsIdx();
                int responseIdx = elapsedTimeAsIdx % responses.size();
                ApiResponse response = responses.get(responseIdx);
                //Thread.sleep( response.getDuration() ); // currently disabled
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(STATUS_OK, 0);

                // only 1 endpoint contains more than 1 line: /api/batches/detailedlog/XXX
                for (String line : response.body) {
                    int lastBatchId = elapsedTimeAsIdx / batchLengths[config.programId];

                    if (requestType == RequestType.STATUS) {
                        line = line.replaceFirst( BATCH_NUMBER_PATTERN, String.valueOf(lastBatchId + 1) );
                    } else if (requestType == RequestType.MAINTENANCE_INFO) {
                        line = line.replace( BATCH_ID_TOKEN, String.valueOf(lastBatchId) )
                                .replace( SERIAL_NO_TOKEN, config.serialNumber );
                    } else if (requestType == RequestType.DETAILED_LOG) {
                        String batchId = String.valueOf( extractId( requestURI ) );
                        line = line.replace( SERIAL_NO_TOKEN, config.serialNumber )
                                .replace( BATCH_ID_TOKEN_2, batchId );
                    } else if (requestType == RequestType.BATCHES) {
                        // returns array of up to 50 last batches
                        int minBatchId = Math.max( getMinBatchNumber(requestURI), lastBatchId - 49 );
                        int batchId = lastBatchId;

                        bodyPS.print("[\n");
                        while (batchId > minBatchId) {
                            bodyPS.print( line.replace(BATCH_ID_TOKEN, String.valueOf(batchId) ) );
                            bodyPS.print(",\n");
                            --batchId;
                        }
                        if (batchId == minBatchId) {
                            bodyPS.print( line.replace(BATCH_ID_TOKEN, String.valueOf(batchId) ) );
                            bodyPS.print("\n");
                        }
                        line = "]";
                    }
                    bodyPS.print(line);
                    // This line is replacing println to avoid problematic '\r' on Windows
                    bodyPS.print('\n');
                }
                long elapsedTimeNanos = System.nanoTime() - processingStartTimeNanos;
                log("Response[%d] sent for: '%s', time_µs=%.0f %n",
                        responseIdx, requestURI, elapsedTimeNanos / 1000.0);
            } else {
                log("unknown endpoint: '%s' %n", pathURI);
                exchange.sendResponseHeaders(STATUS_NOT_FOUND, 0);
            }
        } catch (IOException e) {
            log(e);
            throw new RuntimeException(e);
        }
    }

    private URI createPathURI(URI requestURI, RequestType requestType) {
        String inputPath = requestURI.getPath();
        return URI.create(
                EnumSet.of( RequestType.PROGRAM, RequestType.STATUS, RequestType.BATCHES ).contains( requestType )
                        ? inputPath + '/' + config.programIdStr
                        : requestType == RequestType.DETAILED_LOG
                                // /api/batches/detailedlog/XXX -> cut off /XXX
                                ? inputPath.substring( 0, inputPath.lastIndexOf('/') )
                                        + '/' + config.programIdStr
                                        + '/' + (++requestedBatchLogCount % batchLogsCount[ config.programId ])
                                : inputPath
        );
    }

    private int extractId(URI requestUri) {
        int value;

        try {
            String uriStr = requestUri.toString();
            value = Integer.parseInt( uriStr.substring( uriStr.lastIndexOf('/') + 1 ) );
        } catch (RuntimeException e) {
            value = 0;
        }
        return value;
    }

    private int getElapsedTimeAsIdx() {
        return (int) TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis() - startTime );
    }

    private int getMinBatchNumber(URI requestURI) {
        int value;

        try {
            Matcher m = MIN_BATCH_PATTERN.matcher(requestURI.getQuery());
            m.find();
            value = Integer.parseInt(m.group());
        } catch (RuntimeException e) {
            value = 0;
        }
        return Math.max( value, 1 );
    }
}
